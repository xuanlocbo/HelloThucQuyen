#!/usr/bin/env python3
"""Derive child-visible UI data from locked 5.3 inputs, never from evaluation tables.
Not a media generator; input ZIPs stay byte-identical. Unknown ready scenes fail closed.
"""
import argparse, hashlib, json, pathlib, zipfile, collections

ROOT=pathlib.Path(__file__).resolve().parents[1]
P='app/src/main/assets/htq/'
LOCKS={'runtime':'a54ef041ffd7bfb825a4ff6edb81520c5290ea03720ea064e19c31014b949b62','spec':'4c630f89ec8aa5812da8655780cb470c6cfd6daf956d8a84901a1cefc8d457bb'}
def digest(p):
    with open(p,'rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()
def dump(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
def main():
    ap=argparse.ArgumentParser();ap.add_argument('runtime',type=pathlib.Path);ap.add_argument('spec',type=pathlib.Path);args=ap.parse_args()
    assert digest(args.runtime)==LOCKS['runtime'] and digest(args.spec)==LOCKS['spec']
    with zipfile.ZipFile(args.runtime) as r,zipfile.ZipFile(args.spec) as z:
        def rd(n): return json.loads(r.read(P+n))
        def sd(n): return json.loads(z.read(n))
        nav=rd('engine/v52/navigation.json'); tasks=rd('engine/v52/task_bindings.json'); choices=rd('engine/choice_index.json'); assets=rd('manifest/assets.json'); lessons=rd('content/lessons.json'); acts=rd('content/activities.json')
        task_context=sd('build_spec/linkage_v52/task_source_context.json')
        source=sd('build_spec/source_metadata/stage_3_6/content_bindings.json'); specs=sd('build_spec/linkage_v52/ui_catalog.json'); layout=sd('build_spec/linkage_v52/layout_reference_metrics.json')
        entries=set(r.namelist()); by_choice={x['slot_id']:x for x in choices}; used=set()
        def path(p,kind=None):
            assert isinstance(p,str) and p and not any(x in ('','.','..') for x in p.split('/')) and '\\' not in p and P+p in entries,p
            if kind=='image': assert p.endswith(('.webp','.png')),p
            if kind=='audio': assert p.endswith('.ogg'),p
            used.add(p);return p
        def asset(i,kind='image'):
            a=assets[i]; assert a['type']==kind,(i,kind);return path(a['path'],kind)
        def icon(i):
            a=assets[i];assert a['kind']=='ICO';return path(a['graphics'][0],'image')
        non_task=rd('engine/audio_usage_internal.json')
        routes=collections.defaultdict(set)
        for a in non_task:
            if a.get('task_id') is None:
                for sc in a['screen_ids']: routes[(a['role'],a['reference_id'],a.get('lesson_id'),sc)].add(a['audio_id'])
        def explicit(aid,role,ref,lesson,screen):
            assert aid in routes[(role,ref,lesson,screen)],(aid,role,ref,lesson,screen)
            return asset(aid,'audio')
        def optional_audio(role,ref,lesson,screen):
            ids=routes[(role,ref,lesson,screen)]; return asset(next(iter(ids)),'audio') if len(ids)==1 else None
        all_topics={t['topic_id']:t for t in nav['topic_routes']}
        all_lessons={l['lesson_id']:l for l in lessons}
        def lesson_cover(l):
            # Use an explicitly ordered intro illustration, not the task-context pool.
            i=next((x for x in l['intro_art_ids'] if not x.startswith(('IMG-BG-','IMG-CHR-'))),None)
            return asset(i or all_topics[l['topic_id']]['subject_art_id'])
        areas=[dict(id=x['id'],title=x['name'],image=asset(x['cover_art']),topics=x['topic_ids'],pages=x['pages']) for x in nav['areas']]
        topics=[dict(id=x['topic_id'],title=x['name'],image=asset(x['subject_art_id']),background=asset(x['background_id']),lessons=x['lesson_ids']) for x in nav['topic_routes']]
        less=[]
        for l in lessons:
            lid=l['lesson_id']; intro=[]
            for i in l['intro_art_ids']:
                if i.startswith(('IMG-BG-','IMG-CHR-')): continue
                intro.append(dict(image=asset(i),audio=optional_audio('art_label',i,lid,'SCR-008')))
            # No generated names or synthesized audio. Use only source-authorized clips.
            intro_audio=[explicit(a,'lesson_intro',lid,lid,'SCR-006') for a in l['intro_audio_ids'] if a in routes[('lesson_intro',lid,lid,'SCR-006')]]
            less.append(dict(id=lid,title=l['title'],topic=l['topic_id'],image=lesson_cover(l),levels=l['level_ids'],intro=intro,voice=intro_audio,stories=l['storyboard_ids']))
        levels=[dict(id=x['level_id'],lesson=x['lesson_id'],title=x['name'],tasks=x['task_ids']) for x in acts]
        letter_data=[]
        for l in rd('content/letter_review.json'):
            # Name/isolated phoneme approval remains open, so do not expose that playback.
            letter_data.append(dict(id=l['id'],upper=l['letter'],lower=l['lowercase'],word=l['word_example_vi'],images=[asset(i) for i in l['asset_ids']],audio=explicit(l['example_audio_id'],'letter_example',l['id'],l['lesson_id'],'SCR-010')))
        numbers=[]
        for n in source['number_cards']:
            ref='VI-N-%02d'%n['number']; numbers.append(dict(number=n['number'],label=n['text'],audio=explicit(n['audio_id'],'number_card',ref,'03.03','SCR-011')))
        en_groups=[dict(id=x['id'],title=x['label'],image=asset(x['art'])) for x in source['english_groups']]
        vocab=[]
        for v in source['english_vocabulary']:
            role='english_vocab_core' if v['kind']=='core' else 'english_vocab_optional'
            assert v['kind'] in ('core','optional','extension','extended'),v['kind']
            vocab.append(dict(id=v['id'],lesson=v['lesson_id'],optional=v['kind']!='core',word=v['word_or_phrase'],images=[asset(i) for i in v['asset_ids']],audio=explicit(v['audio_id'],role,v['id'],v['lesson_id'],'SCR-013')))
        phrases=[]
        for v in source['english_phrases']:
            assert len(v['spoken_examples'])==len(v['audio_ids'])
            for i,(line,a) in enumerate(zip(v['spoken_examples'],v['audio_ids'])):
                phrases.append(dict(id=v['id']+'-'+str(i+1),lesson=v['lesson_id'],text=line,meaning=v['meaning_vi'],images=[asset(j) for j in v['asset_ids']],audio=explicit(a,'english_phrase',v['id'],v['lesson_id'],'SCR-014')))
        stories=[]
        for s in rd('content/storyboards.json'):
            story=[]
            for q in s['scene_audio']:
                aid=q['audio_id']; assert any(a['audio_id']==aid and a['role']=='story_scene' and a.get('task_id') is None and s['screen_id'] in a['screen_ids'] for a in non_task)
                story.append(dict(image=asset(q['scene_art_id']),text=q['text'],audio=asset(aid,'audio')))
            stories.append(dict(id=s['id'],lesson=s['lesson_id'],title=s['title'],language=s['language'],screen=s['screen_id'],scenes=story))
        reg=json.loads((ROOT/'qa/screen-register.json').read_text())['screens']
        screens={k:dict(name=v['name'],fixed=[asset(i) for i in v['fixed_art_ids']]) for k,v in reg.items()}
        # The public UI projection contains no evaluation, expected answers, hint/feedback text,
        # answer-label clips, role values, target geometry, or context candidates.
        # Complex scenes are intentionally not presented until their exact renderers exist.
        task_rows=[]; deferred={}; counts=collections.Counter(); total_by=collections.Counter()
        def img(p):return dict(kind='image',path=p)
        def glyph(g):return dict(kind='glyph',text=str(g))
        def group(items,role='items',columns=2):return dict(role=role,columns=columns,items=items,shuffle=False)
        # Reviewed against the original offline prompt and actual source illustrations.
        # Only selected images are emitted; never dump the context candidate pool.
        offline_art={
            '05.07-L3-Q02':['IMG-SCN-0051'],'08.03-L3-Q02':['IMG-SCN-0075'],
            '12.04-L3-Q02':['IMG-SCN-0055'],'13.01-L3-Q02':['IMG-SCN-0087'],
            '13.02-L3-Q01':['IMG-SCN-0054'],'13.02-L3-Q02':['IMG-SCN-0112'],
            '13.06-L3-Q01':['IMG-SCN-0172'],'13.06-L3-Q02':['IMG-SCN-0185'],
            '14.06-L3-Q02':['IMG-SCN-0061'],'15.03-L3-Q02':['IMG-SCN-0172'],
            '15.04-L3-Q02':['IMG-SCN-0085'],'15.05-L3-Q02':['IMG-SCN-0191'],
            '15.06-L3-Q01':['IMG-SCN-0180'],'15.06-L3-Q02':['IMG-SCN-0139'],
            '17.07-L3-Q01':['IMG-SCN-0040'],
            '17.07-L3-Q02':['IMG-SCN-0038','IMG-SCN-0034'],
            '17.09-L3-Q01':['IMG-SCN-0123','IMG-SCN-0239']}
        fixed_context={
            '03.01-L3-Q01':[('IMG-CHR-0064',3),('IMG-FOOD-0012',2)],
            '03.01-L3-Q02':[('IMG-OBJ-0039',4),('IMG-OBJ-0150',4)],
            '05.03-L3-Q01':[('IMG-EDU-0038',1)],'05.03-L3-Q02':[('IMG-EDU-0054',1)],
            '14.01-L1-Q01':[('IMG-CHR-0072',1)],'17.03-L3-Q02':[('IMG-OBJ-0021',1)]}
        # Page partitions come from the authored tray or demonstrably equivalent visual objects.
        # Do not read grading rules or include paired answer maps in the display projection.
        paging_audit={}
        def attach_pages(g, ordered, sizes, pager=None, commit=False):
            assert sum(sizes)==len(ordered)==len(g['items']) and all(1<=n<=6 for n in sizes)
            assert len(set(ordered))==len(ordered)
            g.update(pageSizes=sizes,objectIds=ordered)
            if pager: g['pager']=pager
            if commit: g['advanceRequiresCommit']=True
        def split_sizes(n):
            # Avoid a final lone matching pair when multiple pair pages are needed.
            assert 2<=n<=6
            return {2:[2],3:[3],4:[2,2],5:[2,3],6:[3,3]}[n]
        def visual_key(o):
            if o.get('glyph') is not None:return ('glyph',o['glyph'].casefold())
            return ('art',o['art_id'])
        simple={'SCR-034','SCR-035','SCR-036','SCR-037','SCR-038','SCR-039','SCR-040','SCR-047'}
        for t in tasks:
            tid=t['task_id'];scr=t['screen_id'];s=t['scene'];total_by[scr]+=1
            row=dict(id=tid,choiceSlots=t['choice_slot_ids'],lesson=t['lesson_id'],level=t['level_id'],screen=scr,prompt=t['child_prompt'],background=asset(s['background_id']) if s.get('background_id') else None,ready=False,listen=bool(t['audio'].get('stimulus_segments') or t['audio'].get('story_before_question')),groups=[])
            objects=s['objects']; reason=None; groups=[]
            def choice(o):
                if o.get('visual_path'):
                    q=by_choice[o['object_id']];assert q['task_id']==tid and q['visual_path']==o['visual_path'];return img(path(q['visual_path'],'image'))
                if o.get('glyph') is not None:return glyph(o['glyph'])
                if o['role'] in ('number_tile','numeric_response'):return glyph(o['value'])
                if o.get('render')=='native_unicode_glyph' and o.get('label') is not None:return glyph(o['label'])
                if o.get('render_hands_native') and 'hour' in o:
                    assert o['minute']==0;return dict(kind='clock',path=asset(o['art_id']),hour=o['hour'])
                if o.get('display_clip_polygon_normalized'):
                    rect=o['display_crop_normalized'];points=o['display_clip_polygon_normalized']
                    assert len(rect)==4 and 0<=rect[0]<rect[2]<=1 and 0<=rect[1]<rect[3]<=1 and len(points)>=3
                    assert all(len(v)==2 and all(0<=q<=1 for q in v) for v in points)
                    return dict(kind='polygonCrop',path=asset(o['art_id']),rect=rect,points=points,mirror=bool(o.get('mirror_x')))
                if o.get('scale_policy'):
                    assert o['scale_axis'] in ('height_and_width_uniform','length_only_horizontal') and o['baseline_alignment'] in ('bottom','center_line')
                    return dict(kind='scaled',path=asset(o['art_id']),rect=o['display_crop_normalized'],scale=o['comparison_scale'],axis=o['scale_axis'],alignment=o['baseline_alignment'])
                if o.get('render') in ('native_calendar_card','native_book_page_card'):
                    return dict(kind='labelCard',text=str(o['value']),style='calendar' if o['render']=='native_calendar_card' else 'page')
                if o.get('render') in ('large_label_with_explicit_audio','native_large_listen_card'):
                    # Display the actual neutral destination label; no automatic playback or grading.
                    return dict(kind='labelCard',text=o['label'],style='listen')
                if o.get('render')=='native_fruit_section_diagram':
                    g=o['geometry'];assert g['type']=='annular_section' and g['highlight_style']=='neutral_hatch'
                    return dict(kind='annulus',center=g['center'],outer=g['outer_radius'],inner=g['inner_radius'],region=g['highlight_region'],outerColor=g['outer_boundary_color'],innerColor=g['inner_boundary_color'],stroke=g['outline_width_dp'])
                if o.get('art_id') and not any(k in o for k in ('rect_normalized','geometry','render_hands_native')):
                    if o.get('count') is not None:
                        assert 1<=o['count']<=10 and o.get('distinct_instances');return dict(kind='count',path=asset(o['art_id']),count=o['count'])
                    if o.get('display_crop_normalized'):
                        rect=o['display_crop_normalized'];assert len(rect)==4 and 0<=rect[0]<rect[2]<=1 and 0<=rect[1]<rect[3]<=1
                        return dict(kind='crop',path=asset(o['art_id']),rect=rect,mirror=bool(o.get('mirror_x')))
                    if 'number_overlay' in o:return dict(kind='numbered',path=asset(o['art_id']),text=str(o['number_overlay']))
                    return img(asset(o['art_id']))
                raise ValueError('unimplemented visual geometry')
            # Distinct source instances, not the hidden evaluated answer. Container props stay separate.
            def role_group(roles,kind,columns=3):
                values=[choice(o) for o in objects if o['role'] in roles]
                if values:groups.append(group(values,kind,columns))
            def count_card(a,n):
                assert 0<=n<=10;return dict(kind='count',path=asset(a),count=n)
            story_gate=bool(t['audio'].get('story_before_question'))
            if story_gate:
                first=asset(t['audio']['story_before_question'][0],'audio')
                source_page=next((page for st in stories if st['lesson']==t['lesson_id'] for page in st['scenes'] if page['audio']==first),None)
                row['preludeImage']=source_page['image'] if source_page else None
                row['prelude']=True
                # Prepare the post-listening presentation without making it reachable before the gate.
                assert not t['audio'].get('stimulus_segments'), tid
                if scr in ('SCR-034','SCR-037'):
                    assert all(o['role']=='choice' for o in objects),tid
                    role_group({'choice'},'choices',3)
                elif scr=='SCR-050':
                    assert all(o['role']=='sequence_piece' for o in objects),tid
                    assert s['sequence_slots']==len(objects),tid
                    groups.append(group([dict(kind='blank') for _ in range(s['sequence_slots'])],'sequence_slots',4))
                    role_group({'sequence_piece'},'sequence_pieces',3)
                elif scr=='SCR-066':
                    assert all(o['role'] in ('context','story_card') for o in objects),tid
                    role_group({'context'},'context',1)
                    role_group({'story_card'},'story_cards',3)
                elif scr=='SCR-068':
                    assert all(o['role'] in ('context','choice','result') for o in objects),tid
                    role_group({'context'},'context',1)
                    role_group({'choice'},'choices',3)
                else:raise ValueError('Unknown gated presentation '+tid)
                assert groups and all(g['items'] for g in groups),tid
                for g in groups:
                    g['shuffle']=bool((g['role']=='choices' and s.get('shuffle_choices')) or (g['role']=='sequence_pieces' and s.get('shuffle_pieces')))
                row['afterPrelude']=groups
                # The initial renderer must never read afterPrelude until a real playback-complete event.
                reason='story-before-question gate requires 6.3 ordered playback'
            elif scr=='SCR-054':
                assert set(o['object_id'] for o in objects if o['role']=='countable')==set(s['count_instances'])
                assert set(o['object_id'] for o in objects if o['role']=='scene_prop')==set(s['scene_props'])
                role_group({'scene_prop'},'context',2)
                role_group({'countable'},'countables',5)
                role_group({'choice'},'choices',3)
            elif scr in ('SCR-055','SCR-053') and s.get('selection_pool'):
                groups.append(group([img(asset(q['art_id'])) for q in s['selection_pool'] for _ in range(q['available_count'])],'countables',4))
                groups.append(group([dict(kind='blank')],'workbench',1))
            elif scr=='SCR-055' and s.get('transfer_setup'):
                q=s['transfer_setup'];item=q['item_art_id'];n=q['initial'];reserve=q.get('reserve',0)
                assert len(s['distinct_instance_ids'])==n+reserve
                if q.get('source_art_id'):groups.append(group([img(asset(q['source_art_id']))],'context',1))
                if q.get('direction')=='add' and q.get('destination_art_id'):groups.append(group([img(asset(q['destination_art_id']))],'context',1))
                groups.append(group([img(asset(item)) for _ in range(n)],'countables',5))
                if reserve:groups.append(group([img(asset(item)) for _ in range(reserve)],'reserve',4))
                if q.get('direction')!='add' and q.get('destination_art_id'):groups.append(group([img(asset(q['destination_art_id']))],'workbench',1))
                role_group({'number_tile'},'numbers',4)
            elif scr=='SCR-056' and s.get('split_setup'):
                q=s['split_setup'];assert q['two_bins'] and len(q['counts'])==2
                n,m=q['counts'];arts=q['item_art_ids'];b=img(asset(q['bin_art_id']))
                if q['operation']=='combine':
                    groups.append(group([count_card(arts[0],n),count_card(arts[-1],m)],'count_groups',2))
                    groups.append(group([b],'workbench',1))
                else:
                    assert q['operation']=='split' and 0<=m<=n
                    # All source instances are available; do not display n-m as the solved second bin.
                    groups.append(group([img(asset(arts[0])) for _ in range(n)],'countables',5))
                    groups.append(group([b,b],'bins',2))
                    if m:groups.append(group([glyph(m)],'given_count',1))
            elif scr in ('SCR-050','SCR-053') and (s.get('sequence_slots') or all(o['role'] in ('count_group','size_variant','calendar_or_page') for o in objects)) and objects:
                allowed={'sequence_piece','distractor','count_group','size_variant','calendar_or_page'}
                if all(o['role'] in allowed for o in objects):
                    groups.append(group([dict(kind='blank') for _ in range(s.get('sequence_slots',len(objects)))],'sequence_slots',4))
                    groups.append(group([choice(o) for o in objects],'sequence_pieces',3))
                else:reason='sequence needs its own size or calendar renderer'
            elif scr=='SCR-046' and len(objects)==1 and objects[0]['role']=='adjustable_clock':
                # No initial answer time is encoded in source. Display an unset clock face.
                groups.append(group([dict(kind='clockBlank',path=asset(objects[0]['source_art_reference']))],'context',1))
            elif scr=='SCR-046' and all(o['role'] in ('clock','destination') for o in objects):
                role_group({'clock'},'sources',2);role_group({'destination'},'destinations',2)
            elif scr=='SCR-053' and s.get('direction_input')=='horizontal_swipe_or_two_taps':
                assert all(o['role']=='endpoint' for o in objects) and set(o['position'] for o in objects)=={'left','right'}
                groups.append(group([img(asset(s['book_art_id']))],'context',1))
                groups.append(group([dict(kind='blank'),dict(kind='blank')],'direction_endpoints',2))
            elif scr=='SCR-051' and set(o['role'] for o in objects)=={'movable','container','destination'}:
                role_group({'destination'},'context',1);role_group({'container','movable'},'sources',2)
            elif scr in ('SCR-045','SCR-051') and s.get('native_binding',{}).get('scene_type')=='graded_spatial':
                by_id={o['object_id']:o for o in objects};step=s['native_binding']['steps'][0]
                anchor=by_id[step['anchor_id']];movable=by_id[step['movable_id']]
                zones=[]
                for zid in step['zone_ids']:
                    o=by_id[zid];assert o['role']=='spatial_zone' and o['anchor_id']==anchor['object_id']
                    zones.append(dict(center=o['center_normalized'],size=o['visual_size_normalized']))
                groups.append(group([dict(kind='spatial',path=asset(anchor['art_id']),center=step['anchor_center'],size=step['anchor_size_normalized'],zones=zones)],'board',1))
                groups.append(group([choice(movable)],'sources',1))
            elif scr=='SCR-057' and s.get('measure_setup',{}).get('mode')=='length':
                tar=next(o for o in objects if o['role']=='measure_target');units=[o for o in objects if o['role']=='measure_unit'];m=s['native_binding']['measurement']
                assert len(units)>0 and all(o['unit_length']==m['unit_length'] for o in units) and tar['native_length_units']==m['target_length_units']
                groups.append(group([dict(kind='measureLength',path=asset(tar['art_id']),rect=tar['display_crop_normalized'],unitPath=asset(units[0]['art_id']),unitRect=units[0]['display_crop_normalized'],length=tar['native_length_units'],unitCount=len(units))],'board',1))
                role_group({'numeric_response'},'numbers',4)
            elif scr=='SCR-057' and s.get('measure_setup',{}).get('mode')=='capacity':
                vessels=[]
                for o in objects:
                    if o['role']=='measure_container':
                        assert 0<=o['initial_units']<=o['capacity_units']
                        vessels.append(dict(kind='vessel',path=asset(o['art_id']),label=o['container_key'],fill=o['initial_units']/o['capacity_units']))
                groups.append(group(vessels,'context',2));role_group({'measure_unit'},'sources',1);role_group({'numeric_response'},'numbers',4)
            elif scr=='SCR-063':
                assert all(o['role'] in ('canvas','reference','color_swatch','decoration') for o in objects)
                role_group({'reference'},'context',1);role_group({'canvas'},'workbench',1)
                role_group({'color_swatch'},'palette',4);role_group({'decoration'},'decorations',3)
            elif scr=='SCR-064':
                assert all(o['role'] in ('canvas','context','palette_piece') for o in objects)
                role_group({'canvas','context'},'context',1)
                groups.append(group([dict(kind='blank')],'workbench',1))
                role_group({'palette_piece'},'palette',4)
            elif s.get('canvas_art_id') and s.get('show_target_outline_from_source_only'):
                assert s['target_coordinate_space']=='normalized_full_canvas'
                shapes=[]
                for o in sorted((x for x in objects if x['role']=='destination'),key=lambda x:x['z_index']):
                    assert not o['visible_correctness'] and o['accessible_target_card'] and o['canvas_art_id']==s['canvas_art_id']
                    g=o['geometry'];rect=o['rect_normalized'];assert len(rect)==4 and 0<=rect[0]<rect[2]<=1 and 0<=rect[1]<rect[3]<=1
                    assert g['type'] in ('rect','ellipse','polygon')
                    shapes.append(dict(shape=g['type'],rect=rect,points=g.get('points',[])))
                assert shapes
                groups.append(group([dict(kind='board',path=asset(s['canvas_art_id']),shapes=shapes)],'board',1))
                role_group({'source'},'sources',3)
            elif s.get('word_lines'):
                # Keep each source line, character identity and intentional spaces. A
                # separate sample is a stated stimulus, not a highlighted answer.
                assert scr in ('SCR-040','SCR-059') and all(o['role']=='glyph' for o in objects),tid
                import re
                setup=task_context[tid]['source_visual_setup']
                sample=re.search(r'(?:^Mẫu |mẫu )([A-ZÔĐ])(?:;| riêng\.)',setup)
                if sample:groups.append(group([glyph(sample.group(1))],'sample_glyph',1))
                for line_index,line in enumerate(s['word_lines']):
                    letters=sorted((o for o in objects if o['word_index']==line_index),key=lambda o:o['character_index'])
                    assert [(o['character_index'],o['glyph']) for o in letters]==[(i,c) for i,c in enumerate(line) if not c.isspace()],tid
                    breaks=[i for i in range(1,len(letters)) if letters[i]['character_index']>letters[i-1]['character_index']+1]
                    g=group([glyph(o['glyph']) for o in letters],'word',len(letters))
                    g['objectIds']=[o['object_id'] for o in letters]
                    if breaks:g['wordBreaks']=breaks
                    groups.append(g)
                assert sum(len(g['items']) for g in groups if g['role']=='word')==len(objects),tid
            elif scr in simple:
                if s.get('source_context_reference') and not s.get('fixed_context_art_id'):
                    if tid in fixed_context:
                        setup=fixed_context[tid];assert all(i in s['context_candidates'] for i,n in setup)
                        for i,n in setup:groups.append(group([img(asset(i))] if n==1 else [count_card(i,n)],'context',1))
                        role_group({'choice'},'choices',3)
                    elif tid=='09.01-L1-Q02' and row['listen']:
                        # Source requires the three pictures, with no revealed audio label.
                        role_group({'choice'},'choices',3)
                    elif tid in ('01.05-L1-Q01','01.05-L1-Q02'):
                        ids=['IMG-EDU-0270','IMG-EDU-0263'] if tid.endswith('Q01') else ['IMG-EDU-0263','IMG-EDU-0264']
                        assert all(i in s['context_candidates'] for i in ids)
                        groups.append(group([img(asset(i)) for i in ids],'mix_sources',2))
                        groups.append(group([dict(kind='blank')],'mix_result',1))
                        # Choices stay withheld until the explicit mix event in 6.3.
                    elif tid=='09.04-L1-Q01':
                        assert len(t['audio']['stimulus_segments'])==3 and 'IMG-NAT-0048' in s['context_candidates']
                        groups.append(group([img(asset('IMG-NAT-0048'))],'context',1))
                        groups.append(group([dict(kind='speaker') for _ in range(3)],'listen_options',3))
                    else:reason='source-specific fixed stimulus still needs explicit authored rendering' 
                else:
                    if s.get('fixed_context_art_id'):groups.append(group([img(asset(s['fixed_context_art_id']))],'context',1))
                    if s.get('comparison_context'):
                        for q in s['comparison_context']:
                            groups.append(group([img(asset(q['art_id'])) for _ in range(q['count'])], 'compare_'+q.get('spacing','sample'),min(q['count'],6)))
                    if s.get('clock'):
                        c=s['clock'];assert c['minute']==0
                        groups.append(group([dict(kind='clock',path=asset(c['face_art_id']),hour=c['hour'])],'context',1))
                    if s.get('context_plant_art_id'):groups.append(group([img(asset(s['context_plant_art_id']))],'context',1))
                    try:
                        if any(o['role'] not in ('choice','glyph') for o in objects):raise ValueError('unexpected object role')
                        groups.append(group([choice(o) for o in objects],'word' if s.get('word_lines') else 'choices',3 if s.get('word_lines') else 2))
                    except (KeyError,ValueError):reason='visual geometry requires exact dedicated renderer'
            elif scr in ('SCR-049','SCR-052'):
                # Exact stimulus order and blank slots. Do not use the evaluation sequence.
                prefix=[dict(kind='blank') if v is None else glyph(v) if isinstance(v,int) else img(asset(v)) for v in s['pattern_prefix']]
                blanks=int(s.get('empty_slots',s.get('sequence_slots',0)))
                prefix.extend(dict(kind='blank') for _ in range(blanks))
                assert prefix and all(o['role'] in ('choice','reusable_pattern_tile','number_tile') for o in objects)
                groups.append(group(prefix,'pattern',6))
                groups.append(group([choice(o) for o in objects],'choices',3))
            elif scr in ('SCR-065','SCR-066','SCR-067','SCR-068'):
                # Initial participation scenes only. No result art or object-specific playback.
                allowed={'SCR-065':{'context','choice'},'SCR-066':{'context','story_card','choice'},'SCR-067':{'sound_pad','rest'},'SCR-068':{'context','choice','result'}}[scr]
                if tid=='03.08-L3-Q01':
                    role_group({'context'},'context',1);role_group({'hotspot_choice'},'choices',3)
                elif tid=='12.05-L3-Q02':
                    # The initial career choice is independent. Tool selection must wait for 6.3.
                    role_group({'career_choice'},'choices',3)
                elif tid=='16.05-L3-Q01':
                    role_group({'context'},'context',1);role_group({'canvas'},'workbench',1);role_group({'palette_piece'},'palette',3)
                elif tid=='17.10-L3-Q02':
                    role_group({'sign_card'},'choices',3);role_group({'stroke_reference'},'references',3);role_group({'color_swatch'},'palette',4)
                    groups.append(group([dict(kind='blank')],'workbench',1))
                elif any(o['role'] not in allowed for o in objects):reason='participation-specific geometry or multi-stage role pending' 
                else:
                    try:
                        context=[choice(o) for o in objects if o['role']=='context']
                        options=[choice(o) for o in objects if o['role'] not in ('context','result')]
                        if context:groups.append(group(context,'context',1))
                        if options:groups.append(group(options,'choices',3))
                        if not options or len(context)>3:reason='participation scene layout needs separate review'
                    except (KeyError,ValueError):reason='participation visual geometry pending'
            elif scr=='SCR-058':
                assert all(o['role']=='number_tile' for o in objects)
                groups.append(group([glyph(o['value']) for o in objects],'numbers',3))
            elif scr=='SCR-061':
                assert s['all_face_down'];assert all(o['role']=='memory_card' for o in objects)
                # Face values never enter initial display projection.
                groups.append(group([img(asset(o['back_art_id'])) for o in objects],'memory',3))
                if len(objects)>8:reason='memory group splitting must preserve source pairs'
            elif scr=='SCR-062':groups.append(group([img(asset(s['trace_template_art_id']))],'trace',1))
            elif scr=='SCR-060':
                # Never project the hidden hotspot polygons/keys to the visible screen.
                groups.append(group([img(asset(s['hotspot_base_art_id']))],'hotspot',1))
            elif scr=='SCR-069':
                if len(s['context_candidates'])==1:groups.append(group([img(asset(s['context_candidates'][0]))],'context',1))
                elif tid in offline_art:
                    assert all(i in s['context_candidates'] for i in offline_art[tid])
                    groups.append(group([img(asset(i)) for i in offline_art[tid]],'context',1))
                else:reason='multiple contextual images require source-specific selection' 
            elif scr in ('SCR-041','SCR-042','SCR-043','SCR-044','SCR-048'):
                # Only straightforward two-role pairing/sorting without cropping, sizes, or hidden native geometry.
                src={'source','audio_source','source_group'}; dst={'destination'}
                if s.get('interaction_override')=='single_card_choice' and all(o['role']=='choice' for o in objects):role_group({'choice'},'choices',2)
                elif s.get('columns') and set(o['role'] for o in objects)=={'occupation','work','product'}:
                    object_map={o['object_id']:o for o in objects}
                    assert sorted(i for c in s['columns'] for i in c)==sorted(object_map)
                    for col in s['columns']:groups.append(group([choice(object_map[i]) for i in col],'match_column',2))
                elif s.get('speaker_ids') and all(o['role'] in ('speaker','destination') for o in objects):
                    role_group({'speaker'},'sources',2);role_group({'destination'},'destinations',2)
                elif s.get('table_art_id') and all(o['role'] in ('place_setting','source') for o in objects):
                    groups.append(group([img(asset(s['table_art_id']))],'context',1));role_group({'place_setting'},'destinations',3);role_group({'source'},'sources',3)
                elif any(o['role'] not in src|dst for o in objects):reason='special pairing roles need their own renderer' 
                elif s.get('whole_fruit_art_id'):
                    groups.append(group([img(asset(s['whole_fruit_art_id']))],'context',1));role_group({'source'},'sources',2);role_group({'destination'},'destinations',2)
                elif any(any(k in o for k in ('rect_normalized','geometry','render_hands_native','scale_policy')) for o in objects):reason='pair geometry or counted representation not yet implemented' 
                else:
                    # Audio sources remain unlabeled, but playback for individual source cards belongs to 6.3.
                    try:
                        groups.append(group([dict(kind='speaker') if o['role']=='audio_source' else choice(o) for o in objects if o['role'] in src],'sources',3))
                        groups.append(group([choice(o) for o in objects if o['role'] in dst],'destinations',3))
                        if len(groups[0]['items'])>6 or len(groups[1]['items'])>6:reason='pair-preserving pagination not yet implemented'
                    except (KeyError,ValueError):reason='pair visual geometry pending'
            else:reason='dedicated source-accurate scene renderer not yet implemented'
            if not reason:
                assert groups and all(g['items'] for g in groups),tid
                for g in groups:
                    g['shuffle']=bool((g['role']=='choices' and s.get('shuffle_choices')) or (g['role']=='destinations' and s.get('shuffle_destinations')) or (g['role']=='memory' and s.get('shuffle_cards')) or (g['role']=='sequence_pieces' and s.get('shuffle_pieces')) or (g['role']=='match_column' and s.get('shuffle_each_column')))
                by_role={g['role']:g for g in groups}
                nb=s.get('native_binding') or {}
                object_map={o['object_id']:o for o in objects}
                # These task types have explicit ordered trays in the source.
                if nb.get('tray') and nb.get('scene_type') in ('graded_order','graded_template_placement'):
                    tray=nb['tray'];pages=tray['pages'];ids=[i for pg in pages for i in pg]
                    role='sequence_pieces' if nb['scene_type']=='graded_order' else 'sources'
                    g=by_role[role]
                    assert len(ids)==len(g['items']) and set(ids)<=set(object_map)
                    g['items']=[choice(object_map[i]) for i in ids]
                    g['shuffle']=bool(tray.get('shuffle_within_page',False))
                    attach_pages(g,ids,[len(pg) for pg in pages],commit=tray.get('page_change')=='manual_after_committing_group')
                    if role=='sequence_pieces':
                        slots=sorted(nb['slots'],key=lambda x:x['ordinal']);sg=by_role['sequence_slots']
                        sizes=list(collections.Counter(x['page'] for x in slots).values())
                        assert [x['ordinal'] for x in slots]==list(range(len(slots))) and all(x['empty'] for x in slots)
                        attach_pages(sg,[x['slot_id'] for x in slots],sizes,commit=tray.get('page_change')=='manual_after_committing_group')
                    paging_audit[tid]={'source':'native_binding.tray','pages':[len(pg) for pg in pages]}
                if scr=='SCR-061':
                    # Only back artwork is retained. Group by identical art or upper/lower glyph, never by input adjacency.
                    pairs=collections.defaultdict(list)
                    for o in objects:pairs[visual_key(o)].append(o)
                    assert len(pairs) in (2,3,4) and all(len(v)==2 for v in pairs.values())
                    ids=[o['object_id'] for pair in pairs.values() for o in pair]
                    g=by_role['memory'];g['columns']=2
                    g['items']=[img(asset(object_map[i]['back_art_id'])) for i in ids]
                    sizes=[4,4] if len(ids)==8 else [len(ids)]
                    attach_pages(g,ids,sizes)
                    paging_audit[tid]={'source':'memory_visual_identity','pages':sizes}
                if scr in ('SCR-041','SCR-044') and 'sources' in by_role and 'destinations' in by_role:
                    sg,dg=by_role['sources'],by_role['destinations'];ns,nd=len(sg['items']),len(dg['items'])
                    if tid=='11.03-L3-Q01':
                        # Three place settings; two independent sets of identical utensils, not six inferred pairs.
                        srcs=collections.defaultdict(list)
                        for o in objects:
                            if o['role']=='source':srcs[o['art_id']].append(o)
                        assert sorted(map(len,srcs.values()))==[3,3] and nd==3
                        ids=[o['object_id'] for v in srcs.values() for o in v]
                        sg['items']=[choice(object_map[i]) for i in ids]
                        attach_pages(sg,ids,[3,3]);paging_audit[tid]={'source':'table_art_id + utensil art groups','pages':[3,3]}
                    elif max(ns,nd)>3:
                        assert ns==nd and ns in (4,5,6),tid
                        sources=[o for o in objects if o['role']=='source'];dest=[o for o in objects if o['role']=='destination']
                        assert len(sources)==len(dest)==ns
                        if tid=='03.01-L2-Q02':
                            # Identical fungible source/destination instances; numbered source instances establish page membership.
                            assert len({o['art_id'] for o in sources})==len({o['art_id'] for o in dest})==1
                            sources.sort(key=lambda o:o['instance_number']);dest.sort(key=lambda o:o['instance_number'])
                            assert [o['instance_number'] for o in sources]==[o['instance_number'] for o in dest]==list(range(1,ns+1))
                        else:
                            assert scr=='SCR-044',tid
                            lookup={visual_key(o):o for o in dest}
                            assert len(lookup)==ns and len({visual_key(o) for o in sources})==ns and set(lookup)=={visual_key(o) for o in sources}
                            dest=[lookup[visual_key(o)] for o in sources]
                        sizes=split_sizes(ns)
                        for g,os in [(sg,sources),(dg,dest)]:
                            g['items']=[choice(o) for o in os];g['shuffle']=True
                            attach_pages(g,[o['object_id'] for o in os],sizes,'paired')
                        paging_audit[tid]={'source':'matching visual identity or fungible instance numbers','pages':sizes}
                row.update(ready=True,groups=groups);counts[scr]+=1
            else:deferred[tid]=reason
            task_rows.append(row)
        result=dict(version='5.3-official-1',screens=screens,icons={i:icon(i) for i in ['ICO-001','ICO-002','ICO-003','ICO-007','ICO-010','ICO-020','ICO-021','ICO-043','ICO-050','ICO-051','ICO-055']},areas=areas,topics=topics,lessons=less,levels=levels,letters=letter_data,numbers=numbers,englishGroups=en_groups,vocab=vocab,phrases=phrases,stories=stories,tasks=task_rows,welcomeAudio=optional_audio('ui_voice','welcome',None,'SCR-002'),rewardArt={i:asset(i) for i in sorted({v['garden_item_asset_id'] for v in rd('engine/v52/reward_allocation.json')['task_allocations']})})
        assert len(areas)==6 and len(topics)==17 and len(less)==110 and len(levels)==330 and len(task_rows)==660 and len(choices)==816
        assert len(screens)==69 and len(layout)==851 and len(specs)==398
        assert len(letter_data)==29 and len(numbers)==11 and len(en_groups)==10 and len(vocab)==90 and len(phrases)==20 and len(stories)==10
        assert len(deferred)+sum(counts.values())==660
        assert sorted(q for t in task_rows for q in t['choiceSlots'])==sorted(by_choice)
        dump(ROOT/'app/src/main/assets/htq_ui/content.json',result)
        report=dict(phase='6.2',status='PARTIAL_NATIVE_CANDIDATE_NOT_CI_READY',inputs=LOCKS,screen_count=69,task_count=660,choice_count=816,reference_layouts=len(layout),ui_motion_specs=len(specs),visible_ready_data_tasks=sum(counts.values()),deferred_tasks=len(deferred),gated_post_listening_projections=sum(bool(t.get('afterPrelude')) for t in task_rows),screen_data_coverage={k:dict(ready=counts[k],total=total_by[k]) for k in sorted(total_by)},deferred_by_reason=dict(collections.Counter(deferred.values())),deferred_task_ids=deferred,verified_runtime_asset_paths=len(used),paging_audit=paging_audit,readiness_note='Data ready means a supported initial visual projection only; not a native device test, interaction engine, or final 6.2 approval.')
        dump(ROOT/'qa/projection-audit.json',report)
        print(json.dumps({k:v for k,v in report.items() if k not in ('deferred_task_ids','screen_data_coverage')},ensure_ascii=False,indent=2))
if __name__=='__main__':main()

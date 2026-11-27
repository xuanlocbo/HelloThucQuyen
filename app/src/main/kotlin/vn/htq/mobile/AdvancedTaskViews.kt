package vn.htq.mobile

import android.view.View
import android.widget.LinearLayout
import vn.htq.core.AdvancedInteraction
import vn.htq.core.ui.UiElement
import kotlin.random.Random

/** Native controls for the source-derived advanced scene. All mutations use the same controller as JVM tests. */
internal class AdvancedTaskViews(private val w:GardenWidgets,private val d:AdvancedInteraction,
    private val finished:Boolean,private val pages:MutableMap<String,Int>,private val seed:Long,
    private val commit:(()->Boolean)->Unit,private val refresh:()->Unit,private val playResult:()->Unit){
    private fun title(b:LinearLayout,text:String)=w.add(b,w.text(text,20,true),8)
    private fun button(b:LinearLayout,label:String,enabled:Boolean=true,fn:()->Boolean){w.add(b,w.button(label,"white",enabled=!finished&&enabled,action={commit(fn)}),8)}
    private fun card(c:AdvancedInteraction.Card,mark:Boolean=false,label:String=c.label,onTap:(()->Boolean)?=null):View=w.column().apply{
        background=w.rounded(if(mark)"blue" else "white",if(mark)"navy" else "edge");minimumHeight=w.dp(64);setPadding(w.dp(5),w.dp(6),w.dp(5),w.dp(6));tag=c.id
        contentDescription=label;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        c.path?.let{path->w.add(this,if(c.crop.isEmpty())w.image(path)else w.croppedImage(path,c.crop,false),0,76)}
        w.add(this,w.text(label,if(c.path==null&&label.toIntOrNull()!=null)32 else 18,true),0)
        isSelected=mark
        if(!finished&&onTap!=null){isFocusable=true;setOnClickListener{commit(onTap)}}
    }
    private fun <T> page(b:LinearLayout,key:String,items:List<T>,make:(T)->View){
        if(items.isEmpty()){w.add(b,w.text("Chưa có vật",18));return}
        val k="advanced:${d.task.id}:$key";val chunks=items.chunked(w.viewport.pageSize(6));val n=(pages[k]?:0).coerceIn(0,chunks.lastIndex)
        w.grid(b,chunks[n].map(make),3,80,w.taskBoardDp)
        if(chunks.size>1){w.add(b,w.text("${n+1} / ${chunks.size}",18),4);w.pair(b,w.button("Trước","white",enabled=n>0,action={pages[k]=n-1;refresh()}),w.button("Sau","green",enabled=n<chunks.lastIndex,action={pages[k]=n+1;refresh()}))}
    }
    private fun itemGroup(b:LinearLayout,key:String,label:String,art:String?,items:List<AdvancedInteraction.Card>,canMove:Boolean=true){
        title(b,label);art?.let{w.art(b,it,104)}
        page(b,key,items){c->card(c,d.pending==c.id,onTap=if(canMove){ { if(d.isSplit&&!d.combine)d.selectSource(c.id)else d.move(c.id) } }else null)}
    }
    fun draw(b:LinearLayout){
        when{
            d.isMix->{title(b,"Chọn hai màu để pha");page(b,"pigments",d.mixInputs){c->card(c,c.id in d.mixSelection,onTap={d.choosePigment(c.id)})};button(b,"Pha màu",!d.resultViewed&&d.mixSelection.size==2){d.viewResult()};if(d.resultViewed){title(b,"Màu sau khi pha");d.visibleResultCards.forEach{w.add(b,card(it))};title(b,"Bé chọn màu");page(b,"mix_options",d.predictionCards.shuffled(Random(seed xor d.task.id.hashCode().toLong()))){c->card(c,c.id==d.predictionId,onTap={d.predict(c.id)})}}}
            d.isSimulation->{title(b,"Quan sát");page(b,"initial",d.simulationInputs){card(it)};title(b,"Bé dự đoán");page(b,"prediction",d.predictionCards.shuffled(Random(seed xor d.task.id.hashCode().toLong()))){c->card(c,c.id==d.predictionId,onTap=if(d.resultViewed)null else { {d.predict(c.id)} })};button(b,"Xem kết quả",!d.resultViewed){d.viewResult()};if(d.resultViewed){d.visibleResultCards.forEach{w.add(b,card(it))};if(d.task.id=="16.03-L1-Q01")w.add(b,w.button("Nghe tiếp câu chuyện","teal",action=playResult));if(d.waterComparison)w.add(b,w.text("Nước có thể đi qua các khe giữa hạt cát rời. Nước không đi xuyên qua viên đá đặc trong mô hình này.",20,center=false))}}
            d.isCareer->{title(b,"Bé chọn nghề");page(b,"career",d.careerCards){c->card(c,c.id==d.careerId,onTap={d.chooseCareer(c.id)})};if(d.careerId!=null){title(b,"Bé chọn hai dụng cụ");page(b,"tools",d.toolCards){c->card(c,c.id in d.chosenToolIds,onTap={d.chooseTool(c.id)})}}}
            d.isTransfer->{
                val initial=d.instances.take(d.initialTransfer);val movable=d.instances.filter{d.transferAvailable(it.id)};val moved=movable.filter{it.id in d.movedIds};val remaining=movable.filterNot{it.id in d.movedIds}
                if(d.addDirection){itemGroup(b,"reserve","Bên ngoài",null,remaining);title(b,"Trong giỏ");d.destinationArt?.let{w.art(b,it,104)};page(b,"initial",initial){card(it)};page(b,"moved",moved){card(it,true,onTap={d.move(it.id)})}}
                else{itemGroup(b,"source","Bên ngoài",d.sourceArt,remaining);itemGroup(b,"dest","Đã chuyển",d.destinationArt,moved)}
            }
            d.isSplit&&d.combine->{
                val remain=d.instances.filterNot{it.id in d.movedIds}.toSet()
                itemGroup(b,"bin1","Nhóm thứ nhất",d.destinationArt,d.instances.take(d.combineFirst).filter{it in remain})
                itemGroup(b,"bin2","Nhóm thứ hai",d.destinationArt,d.instances.drop(d.combineFirst).filter{it in remain})
                itemGroup(b,"combined","Nhóm đã gộp",d.destinationArt,d.instances.filter{it.id in d.movedIds})
            }
            d.isSplit->{
                if(d.twoRounds)title(b,"Cách chia ${d.committedRounds.size+1}")
                itemGroup(b,"unplaced","Chưa chia",null,d.instances.filter{it.id !in d.assignments})
                itemGroup(b,"left","Giỏ thứ nhất",d.destinationArt,d.instances.filter{d.assignments[it.id]=="left"})
                itemGroup(b,"right","Giỏ thứ hai",d.destinationArt,d.instances.filter{d.assignments[it.id]=="right"})
                w.pair(b,w.button("Đặt giỏ thứ nhất","green",enabled=!finished&&d.pending!=null,action={commit{d.drop("left")}}),w.button("Đặt giỏ thứ hai","green",enabled=!finished&&d.pending!=null,action={commit{d.drop("right")}}))
                button(b,"Đưa ra ngoài",d.pending!=null){d.drop("source")}
                if(d.twoRounds&&d.committedRounds.isEmpty())button(b,"Lưu cách chia này",d.assignments.size==d.instances.size){d.commitRound()}
                d.committedRounds.firstOrNull()?.let{round->title(b,"Cách chia trước");round.forEachIndexed{i,ids->itemGroup(b,"previous$i","Giỏ ${i+1}",d.destinationArt,d.instances.filter{it.id in ids},false)}}
            }
            d.isPool->{if(d.twoStages)title(b,"Lượt ${d.committedStages.size+1}");page(b,"pool",d.instances){c->card(c,c.id in d.selectedIds,onTap={d.choose(c.id)})};if(d.twoStages&&d.committedStages.isEmpty())button(b,"Xong lượt này"){d.commitStage()}}
            d.isClock->{w.add(b,w.clock(d.hour,d.minute),0,if(w.viewport.wide)210 else 270);title(b,"Kim giờ");w.pair(b,w.button("Giờ −","white",enabled=!finished,action={commit{d.setHour(if(d.hour==1)12 else d.hour-1)}}),w.button("Giờ +","green",enabled=!finished,action={commit{d.setHour(if(d.hour==12)1 else d.hour+1)}}));title(b,"Kim phút");w.pair(b,w.button("Phút −","white",enabled=!finished,action={commit{d.setMinute((d.minute+55)%60)}}),w.button("Phút +","green",enabled=!finished,action={commit{d.setMinute((d.minute+5)%60)}}))}
            d.isHotspot->{val path=requireNotNull(d.hotspotPath);val key="advanced:${d.task.id}:zoom";val zoom=pages[key]==1;w.add(b,w.zoomArt(path,zoom,d.point,if(finished)null else {x,y->commit{d.touch(x,y)}}),0,if(w.viewport.wide)240 else 340);w.add(b,w.button(if(zoom)"Thu nhỏ" else "Xem lớn","white",action={pages[key]=if(zoom)0 else 1;refresh()}))}
            d.isLength->{d.lengthBoard?.let{w.add(b,w.measureLength(it,d.placedUnits.size),0,220)};title(b,"Chọn que đặt nối tiếp");page(b,"units",d.lengthUnits.filter{c->d.placedUnits.none{it.id==c.id}}){c->card(c,onTap={d.addUnit(c.id)})}}
            d.isCapacity->{d.cup?.let{w.add(b,card(it,d.cupReady,onTap={d.selectCup()}))};title(b,"Chọn bình để rót một cốc");w.grid(b,d.vessels.map{v->w.column().apply{background=w.rounded("white");minimumHeight=w.dp(64);contentDescription="Bình ${v.label}";w.add(this,w.vessel(UiElement.Vessel(v.path,v.label,d.fill(v))),0,180);w.add(this,w.text("Bình ${v.label}",20,true));if(!finished&&d.cupReady){isFocusable=true;setOnClickListener{commit{d.pour(v.id)}}}}},2,100,w.taskBoardDp)}
            d.isShape->{d.board?.let{board->val shapes=d.targetCards.map{d.shapeTargets.getValue(it.id)};val placements=d.targetCards.mapNotNull{target->d.assignments.entries.firstOrNull{it.value==target.id}?.let{e->val source=d.sourceCards.single{it.id==e.key};GardenWidgets.BoardPlacement(requireNotNull(source.path),source.crop,target.crop)}};w.add(b,w.outlineBoard(board.path,shapes,placements,true),0,280)};title(b,"Chọn hình");page(b,"pieces",d.sourceCards.shuffled(Random(seed xor d.task.id.hashCode().toLong()))){c->card(c,d.pending==c.id||c.id in d.assignments,onTap={d.selectSource(c.id)})};title(b,"Đặt vào vị trí");page(b,"targets",d.targetCards){c->card(c,d.assignments.containsValue(c.id),onTap={d.drop(c.id)})}}
            d.isSpatial->{d.spatialSteps.forEachIndexed{i,s->title(b,"Lượt ${i+1}");val ix=s.zones.indexOfFirst{it.id==d.assignments[s.source.id]};w.add(b,w.spatial(s.anchor,if(ix>=0)s.source.path else null,ix,s.zoneDepths.getOrElse(ix){0}),0,260);w.add(b,card(s.source,d.pending==s.source.id,onTap={d.selectSource(s.source.id)}));page(b,"zones$i",s.zones){c->card(c,c.id==d.assignments[s.source.id],onTap={d.drop(c.id)})}}}
            d.isActions->{title(b,"Chọn vật");page(b,"action_source",d.sourceCards){c->card(c,d.pending==c.id,onTap={d.selectSource(c.id)})};title(b,"Đặt vật vào đâu?");d.sourceCards.filter{it.id!=d.pending}.forEach{c->w.add(b,card(c));w.pair(b,w.button("Đặt vào trong","white",enabled=!finished&&d.pending!=null,action={commit{d.drop(c.id,"place_inside")}}),w.button("Đặt lên","white",enabled=!finished&&d.pending!=null,action={commit{d.drop(c.id,"place_on")}}))};d.committedActions.forEach{a->w.grid(b,listOf(d.sourceCards.single{it.id==a.objectId},d.sourceCards.single{it.id==a.targetId}).map{card(it,true)},2,80,w.taskBoardDp)}}
            d.isChains->{d.chainColumns.forEachIndexed{i,col->title(b,listOf("Nghề","Công việc","Sản phẩm")[i]);page(b,"chain$i",col.shuffled(Random(seed xor d.task.id.hashCode().toLong() xor i.toLong()))){c->card(c,c.id in d.selectedIds,onTap={d.chainTap(c.id)})}};d.committedChains.forEachIndexed{i,chain->title(b,"Nhóm ${i+1}");w.grid(b,chain.map{id->card(d.chainColumns.flatten().single{it.id==id},true)},3,80,w.taskBoardDp)}}
        }
        if(d.numbers.isNotEmpty()){title(b,"Bé chọn số");page(b,"numbers",d.numbers){c->card(c,c.id==d.selectedNumber,onTap={d.setNumber(c.id)})}}
    }
}

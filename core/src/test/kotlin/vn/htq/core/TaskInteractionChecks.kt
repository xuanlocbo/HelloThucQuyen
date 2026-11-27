package vn.htq.core

import vn.htq.core.ui.*

/** Exercise the same controller used by native views, with real source IDs. */
internal object TaskInteractionChecks {
    fun run(c:Catalog,ui:UiContent) {
        val picture=IntArray(81){0xffffffff.toInt()}
        for(i in 2..6){picture[2*9+i]=0xff000000.toInt();picture[6*9+i]=0xff000000.toInt();picture[i*9+2]=0xff000000.toInt();picture[i*9+6]=0xff000000.toInt()}
        check(FillRegions.boundedMask(picture,9,9,4,4,dilation=0)?.count{it}==9)
        check(FillRegions.boundedMask(picture,9,9,0,0,dilation=0)==null)
        check(FillRegions.boundedMask(picture,9,9,2,2,dilation=0)==null)
        val hotspot=c.open("engine/hotspots.json").bufferedReader().use{Json.parse(it.readText()).arr().map{v->v.obj()}}
        val backend=ControlledAudio();val arbiter=AudioArbiter(backend);val gate=ListeningGate(c,arbiter)
        var supported=0;var restored=0;var wrong=0;val counts=linkedMapOf<String,Int>()
        val taskMotion=TaskMotion(c);var motionCases=0
        val session=SessionPolicy(c);var sessionRoundTrips=0
        for(t in ui.tasks.values){
            val s=TaskInteraction(c,t);if(!s.supported)continue
            counts[s.type]=(counts[s.type]?:0)+1
            check(taskMotion.request(s,true,false)==MotionDecision.Blocked)
            val g=if(t.prelude)t.afterPrelude else t.groups
            val roles=g.flatMap{gr->gr.objectIds.map{it to gr.role}}.toMap()
            fun tap(id:String){check(s.tap(roles.getValue(id),id)){"Input rejected $id in ${t.id}"}}
            val rule=c.assessment.getValue(t.id).o("rule");val scene=c.tasks.getValue(t.id).o("scene")
            val reference=InteractionChecks.reference(t.id,rule,scene,hotspot)
            // Arbitrary ID never becomes a response; no hidden answer is manufactured by controller.
            check(!s.tap("choices","invalid"));check(!s.tap("sources","invalid"))
            if(s.advanced!=null){
                val d=s.advanced
                check(!d.move("foreign"));check(!d.choose("foreign"));check(!d.setNumber("foreign"))
                check(!d.touch(Double.NaN,0.5));check(!d.addUnit("foreign"));check(!d.drop("foreign"));check(!d.pour("foreign"))
                when(reference){
                    is Response.Single->{check(d.isMix);check(d.response()==null);check(!d.viewResult());check(!d.predict(reference.id));d.mixInputs.forEach{check(d.choosePigment(it.id))};check(d.viewResult());check(d.visibleResultCards.isNotEmpty());check(d.predict(reference.id))}
                    Response.ParticipationDone->{if(d.isSimulation){check(d.visibleResultCards.isEmpty());check(d.predict(d.predictionCards.first().id));check(d.viewResult());check(d.visibleResultCards.isNotEmpty());check(!d.predict(d.predictionCards.first().id))}else if(d.isCareer){check(!d.chooseTool(d.toolCards.first().id));check(d.chooseCareer(d.careerCards.first().id));d.toolCards.take(2).forEach{check(d.chooseTool(it.id))};check(!d.chooseTool(d.toolCards[2].id))}else error("Unexpected open task")}

                    is Response.Transfer->{reference.movedIds.forEach{check(d.move(it))};reference.numberId?.let{check(d.setNumber(it))}}
                    is Response.Combine->{reference.movedIds.forEach{check(d.move(it))};check(d.setNumber("number:${reference.number}"))}
                    is Response.Split->{reference.rounds.forEachIndexed{i,round->round[0].forEach{check(d.move(it,"left"))};round[1].forEach{check(d.move(it,"right"))};if(i<reference.rounds.lastIndex)check(d.commitRound())};reference.number?.let{check(d.setNumber("number:$it"))}}
                    is Response.Selection->reference.ids.forEach{check(d.choose(it))}
                    is Response.QuantitySteps->reference.steps.forEachIndexed{i,ids->ids.forEach{check(d.choose(it))};if(i<reference.steps.lastIndex)check(d.commitStage())}
                    is Response.Length->{reference.units.forEach{check(d.addUnit(it.id))};check(d.setNumber(reference.numberId))}
                    is Response.Capacity->{reference.pours.forEach{check(d.selectCup());check(d.pour(it.containerId))};check(d.setNumber(reference.numberId))}
                    is Response.Clock->{check(d.setHour(reference.hour));check(d.setMinute(reference.minute))}
                    is Response.Hotspot->check(d.touch(reference.x,reference.y))
                    is Response.Pairs->reference.pairs.forEach{(a,b)->check(d.selectSource(a));check(d.drop(b))}
                    is Response.Spatial->reference.placements.forEach{(a,b)->check(d.selectSource(a));check(d.drop(b))}
                    is Response.Actions->reference.actions.forEach{check(d.selectSource(it.objectId));check(d.drop(it.targetId,it.verb))}
                    is Response.Chains->reference.chains.flatten().forEach{check(d.chainTap(it))}
                    else->error("Unexpected advanced fixture ${t.id}")
                }
                val saved=d.saveFields();val correct=d.response();check(correct!=null);check(d.undo());check(d.restoreFields(saved));check(d.response()==correct)
                val tampered=mapOf("valid_committed_response" to mapOf("schema" to "htq.interaction.events.v1","events" to listOf(listOf("move","foreign","destination"))))
                check(!d.restoreFields(tampered));check(d.response()==correct)
            }else if(s.creative!=null){
                val d=s.creative;check(reference==Response.ParticipationDone);check(!d.pickPalette("foreign"));check(!d.pad("foreign"))
                when{
                    d.build->{check(d.pickPalette(d.palette.first().id));val slot=d.buildSlots.firstOrNull();check(d.place(0.5f,0.5f,slot));check(d.placedPieces.size==1);check(d.pickPiece(d.placedPieces.first().id));if(d.allowRotate)check(d.rotate());if(d.allowScale)check(d.scale())}
                    d.rhythm->{check(d.pad(d.pads.first().id));d.pads.getOrNull(1)?.let{check(d.pad(it.id))};check(d.committedBeats.isNotEmpty())}
                    else->{if(d.sign){check(d.chooseSign(d.signs.first().id));check(d.chooseReference(d.references.first().id))};d.colors.firstOrNull()?.let{check(d.selectColor(it.id))};check(d.setBrush("brush"));val crop=d.sourceCrop;check(!d.stroke(listOf(-0.1f to 0.5f)));check(d.stroke(listOf((crop[0]+(crop[2]-crop[0])*.3f) to (crop[1]+(crop[3]-crop[1])*.3f),(crop[0]+(crop[2]-crop[0])*.6f) to (crop[1]+(crop[3]-crop[1])*.6f))));check(d.committedMarks.size==1)}
                }
                val saved=d.saveFields();check(d.undo());check(d.redo());check(d.saveFields()==saved);val copy=TaskInteraction(c,t);check(copy.restoreFields(Json.parse(Json.encode(saved)).obj()));check(Json.encode(copy.saveFields())==Json.encode(saved))
                val bad=mapOf("valid_committed_response" to mapOf("schema" to "htq.creative.events.v1","events" to listOf(mapOf("op" to "pad","id" to "foreign","index" to 0))))
                check(!copy.restoreFields(bad));check(Json.encode(copy.saveFields())==Json.encode(saved))
            }else when(reference){
                is Response.Single-> {
                    val incorrect=roles.keys.firstOrNull{it!=reference.id&&s.canTap(roles.getValue(it),it)}
                    if(incorrect!=null){tap(incorrect);if(!t.prelude){check(s.submit(null)==Verdict.Retry);wrong++};s.clear()}
                    tap(reference.id)
                }
                is Response.Selection->reference.ids.forEach(::tap)
                is Response.Sequence->reference.ids.forEach(::tap)
                is Response.Pairs->reference.pairs.forEach{(a,b)->tap(a);tap(b)}
                Response.ParticipationDone-> {
                    // Participation requires only explicit finish per source; no correct/wrong claim.
                    check(c.childTask(t.id).participation)
                    val option=roles.entries.firstOrNull{s.canTap(it.value,it.key)}
                    if(option!=null&&!s.isPairing)tap(option.key)
                }
                else->error("Unsupported controller family was marked ready")
            }
            check(s.response()!=null){"No committed response for ${t.id}"}
            // Exercise the exact allowlist and JSON roundtrip used by SQLite, not only controller snapshots.
            val persisted=requireNotNull(session.sanitize(Json.parse(Json.encode(session.snapshotForTask(t.id)+s.saveFields())).obj()))
            val restoredSession=TaskInteraction(c,t)
            check(restoredSession.restoreFields(persisted)){"Stored session rejected for ${t.id}"}
            check(restoredSession.response()==s.response()){"Stored session changed response for ${t.id}"}
            check(Json.encode(restoredSession.saveFields())==Json.encode(s.saveFields())){"Stored session dropped committed state for ${t.id}"}
            sessionRoundTrips++
            if(s.advanced?.isSimulation==true){
                check(taskMotion.available(s));check(taskMotion.request(s,false,false)==MotionDecision.Blocked)
                val restoredState=TaskInteraction(c,t);check(restoredState.restoreFields(Json.parse(Json.encode(s.saveFields())).obj()))
                check(taskMotion.request(restoredState,false,false)==MotionDecision.Blocked)
            }
            if(reference!=Response.ParticipationDone){
                val copy=TaskInteraction(c,t);check(copy.restoreFields(Json.parse(Json.encode(s.saveFields())).obj()));check(copy.response()==s.response());restored++
                if(s.advanced==null)check(!copy.restoreFields(mapOf("selected_object_ids" to listOf("invalid"),"sequence_ids" to listOf("invalid"))))
            }
            var receipt:ListeningReceipt?=null
            if(t.prelude){check(s.submit(null)==Verdict.NotSubmitted);gate.enter(t.id);check(gate.start(t.id){check(it)});while(arbiter.active)backend.finish();receipt=requireNotNull(gate.receipt(t.id))}
            val v=s.submit(receipt);check(v is Verdict.Completed){"Controller failed ${t.id} $v"};check(s.submit(receipt)==Verdict.NotSubmitted);check(!s.tap("choices",roles.keys.firstOrNull()?:"invalid"))
            if(taskMotion.available(s)){
                val normal=taskMotion.request(s,true,false) as MotionDecision.Media
                val reduced=taskMotion.request(s,true,true) as MotionDecision.Media
                check(normal.path.endsWith(".webp")&&normal.path in c.paths)
                check(reduced.path.endsWith(".png")&&reduced.path in c.paths&&reduced.reducedSteps.all{it in c.paths})
                if(t.id=="16.03-L1-Q01")check(normal.path.endsWith("mot_091_seed_story.webp"))
                if(t.id=="06.06-L1-Q02")check(normal.path.endsWith("mot_098_butterfly.webp"))
                check(taskMotion.request(s,false,false)==MotionDecision.Blocked);motionCases++
            }
            gate.leave();supported++
        }
        check(sessionRoundTrips==660);println("HTQ_SESSION_ROUNDTRIP_PASS tasks=$sessionRoundTrips")
        check(motionCases==12);println("HTQ_TASK_MOTION_PASS cases=$motionCases")
        check(supported==660);println("HTQ_TASK_CONTROLLERS_PASS tasks=$supported restored=$restored wrong=$wrong counts=$counts")
        val fake=object:AudioBackend{
            var now=0L;var cb:((Boolean)->Unit)?=null;val waits=mutableListOf<Long>();val played=mutableListOf<String>()
            override fun nowMs()=now
            override fun play(path:String,onComplete:(Boolean)->Unit){played.add(path);cb=onComplete}
            override fun waitFor(ms:Long,onComplete:(Boolean)->Unit){waits.add(ms);cb=onComplete}
            override fun stop(){cb=null}
            fun finish(elapsed:Long){now+=elapsed;val f=cb;cb=null;f?.invoke(true)}
        }
        val rhythmAudio=AudioArbiter(fake);val policy=TaskAudioPolicy(c);val id="04.04-L3-Q02"
        val req=requireNotNull(policy.rhythmRequest(id,listOf("$id:o01","$id:o02","$id:o01"),true));var done=false
        rhythmAudio.request(req){done=it};fake.finish(250);check(fake.waits==listOf(600L));fake.finish(600);check(fake.waits==listOf(600L,850L));fake.finish(850);fake.finish(1000)
        check(done&&!rhythmAudio.active&&fake.played.size==2);check(policy.rhythmRequest(id,listOf("foreign"),true)==null)
        rhythmAudio.request(req);val stale=fake.cb;rhythmAudio.stop();stale?.invoke(true);check(!rhythmAudio.active);println("HTQ_RHYTHM_QUEUE_PASS")

    }
}

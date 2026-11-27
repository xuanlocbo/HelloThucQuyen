package vn.htq.core

import vn.htq.core.ui.*

/** Exercise the same controller used by native views, with real source IDs. */
internal object TaskInteractionChecks {
    fun run(c:Catalog,ui:UiContent) {
        val hotspot=c.open("engine/hotspots.json").bufferedReader().use{Json.parse(it.readText()).arr().map{v->v.obj()}}
        val backend=ControlledAudio();val arbiter=AudioArbiter(backend);val gate=ListeningGate(c,arbiter)
        var supported=0;var restored=0;var wrong=0;val counts=linkedMapOf<String,Int>()
        for(t in ui.tasks.values){
            val s=TaskInteraction(c,t);if(!s.supported)continue
            counts[s.type]=(counts[s.type]?:0)+1
            val g=if(t.prelude)t.afterPrelude else t.groups
            val roles=g.flatMap{gr->gr.objectIds.map{it to gr.role}}.toMap()
            fun tap(id:String){check(s.tap(roles.getValue(id),id)){"Input rejected $id in ${t.id}"}}
            val rule=c.assessment.getValue(t.id).o("rule");val scene=c.tasks.getValue(t.id).o("scene")
            val reference=InteractionChecks.reference(t.id,rule,scene,hotspot)
            // Arbitrary ID never becomes a response; no hidden answer is manufactured by controller.
            check(!s.tap("choices","invalid"));check(!s.tap("sources","invalid"))
            when(reference){
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
            if(reference!=Response.ParticipationDone){
                val copy=TaskInteraction(c,t);check(copy.restoreFields(Json.parse(Json.encode(s.saveFields())).obj()));check(copy.response()==s.response());restored++
                check(!copy.restoreFields(mapOf("selected_object_ids" to listOf("invalid"),"sequence_ids" to listOf("invalid"))))
            }
            var receipt:ListeningReceipt?=null
            if(t.prelude){check(s.submit(null)==Verdict.NotSubmitted);gate.enter(t.id);check(gate.start(t.id){check(it)});while(arbiter.active)backend.finish();receipt=requireNotNull(gate.receipt(t.id))}
            val v=s.submit(receipt);check(v is Verdict.Completed){"Controller failed ${t.id} $v"};check(s.submit(receipt)==Verdict.NotSubmitted);check(!s.tap("choices",roles.keys.firstOrNull()?:"invalid"));gate.leave();supported++
        }
        check(supported>=500);println("HTQ_TASK_CONTROLLERS_PASS tasks=$supported restored=$restored wrong=$wrong counts=$counts")
    }
}

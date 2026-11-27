package vn.htq.core

/** Deterministic playback backend, only in tests. Simulates each actual backend completion callback. */
internal class ControlledAudio:AudioBackend {
    var callback:((Boolean)->Unit)?=null;private set
    var played=0;private set
    override fun play(path:String,onComplete:(Boolean)->Unit){check(callback==null);callback=onComplete;played++}
    override fun stop(){callback=null}
    fun finish(ok:Boolean=true){val f=requireNotNull(callback);callback=null;f(ok)}
}
internal object AssessmentChecks {
    fun run(c:Catalog, onCompletion:(CompletionTicket)->Unit={}){
        val evaluator=AssessmentEngine(c);val h=c.open("engine/hotspots.json").bufferedReader().use{Json.parse(it.readText()).arr().map{v->v.obj()}}
        val backend=ControlledAudio();val audio=AudioArbiter(backend);val gate=ListeningGate(c,audio)
        var supported=0;var invalid=0;var equivalent=0;var gated=0
        for((id,spec) in c.assessment){
            val rule=spec.o("rule");val scene=c.tasks.getValue(id).o("scene");val r=InteractionChecks.reference(id,rule,scene,h)
            check(evaluator.evaluate(id,r,false)==Verdict.NotSubmitted)
            var receipt:ListeningReceipt?=null
            if(gate.required(id)){
                gated++;gate.enter(id);check(evaluator.evaluate(id,r,true)==Verdict.NotSubmitted)
                var ended=false
                check(gate.start(id){ok->check(ok);ended=true});val old=backend.callback
                audio.stop();old?.invoke(true);check(!ended&&gate.receipt(id)==null)
                check(gate.start(id){ok->check(!ok)});backend.finish(false);check(gate.receipt(id)==null)
                check(gate.start(id){ok->check(ok);ended=true});val first=backend.callback
                while(audio.active){backend.finish();if(audio.active)check(gate.receipt(id)==null&&!ended);first?.invoke(true)}
                check(ended);receipt=requireNotNull(gate.receipt(id))
            }
            val completed=evaluator.evaluate(id,r,true,receipt) as Verdict.Completed
            check(completed.ticket.taskId==id&&completed.ticket.kind==(if(spec.b("no_grade_open"))CompletionKind.OPEN else CompletionKind.GRADED))
            onCompletion(completed.ticket);supported++
            for(bad in InteractionChecks.invalid(r)){check(evaluator.evaluate(id,bad,true,receipt) !is Verdict.Completed){"Accepted invalid $id $bad"};invalid++}
            val alternate=when(r){
                is Response.Pairs->r.copy(pairs=if(rule.s("type")=="memory_pairs")r.pairs.reversed().map{it.second to it.first}else r.pairs.reversed())
                is Response.Selection->r.copy(ids=r.ids.reversed())
                is Response.Chains->r.copy(chains=r.chains.reversed())
                else->null
            }
            if(alternate!=null){check(evaluator.evaluate(id,alternate,true,receipt) is Verdict.Completed);equivalent++}
            if(receipt!=null){gate.leave();check(evaluator.evaluate(id,r,true,receipt)==Verdict.NotSubmitted)}
        }
        check(supported==660&&gated==39&&evaluator.implementedRules==c.ruleTypes&&c.ruleTypes.size==23)
        println("ASSESSMENT_OK supported_tasks=$supported deferred_tasks=0 supported_rule_types=${evaluator.implementedRules.size} all_rule_types=${c.ruleTypes.size}")
        println("HTQ_INTERACTION_RULES_PASS valid=$supported invalid=$invalid equivalent=$equivalent listening_gates=$gated")
        // Duplicate completion of the first clip must not bypass remaining clips.
        var completed=0;val request=AudioRequest(listOf("a","b","c"));audio.request(request){check(it);completed++}
        val stale=backend.callback;backend.finish();stale?.invoke(true);check(audio.active&&completed==0)
        backend.finish();check(audio.active&&completed==0);backend.finish();check(!audio.active&&completed==1);stale?.invoke(true);check(completed==1)
        println("HTQ_AUDIO_GATE_PASS stale_and_duplicate_callbacks_rejected=true")
    }
}

package vn.htq.mobile

import android.content.SharedPreferences

/** Local, parent-authorized preferences only. Required listening is never completed by a muted clip. */
internal data class ParentPreferences(
    val voice:Boolean=true,val english:Boolean=true,val music:Boolean=false,val effects:Boolean=true,
    val speechVolume:Int=85,val musicVolume:Int=20,val effectVolume:Int=40,val speedIndex:Int=2,
    val minutes:Int=20,val remind:Boolean=true,val largeText:Boolean=false,val reducedMotion:Boolean=false
){
    data class Playback(val gain:Float,val speed:Float)
    val speed get()=listOf(.75f,.85f,1f,1.15f,1.25f)[speedIndex]
    fun playback(path:String):Playback?{
        val family=path.substringAfterLast('/').removePrefix("aud_").substringBefore('_')
        val (enabled,volume,rate)=when(family){
            "mus"->Triple(music,musicVolume,1f)
            "fx"->Triple(effects,effectVolume,1f)
            "env","est"->Triple(english,speechVolume,speed)
            "vif","vig","vih","vil","vik","vin","vic","vst"->Triple(voice,speechVolume,speed)
            // Source listening samples, instruments, rhythm and songs keep their original rate/gain.
            else->Triple(true,100,1f)
        }
        return if(enabled&&volume>0)Playback(volume/100f,rate)else null
    }
    fun save(p:SharedPreferences):Boolean=p.edit().putBoolean("voice",voice).putBoolean("english",english)
        .putBoolean("music",music).putBoolean("effects",effects).putInt("speech_volume",speechVolume)
        .putInt("music_volume",musicVolume).putInt("effect_volume",effectVolume).putInt("speed_index",speedIndex)
        .putInt("study_minutes",minutes).putBoolean("remind_rest",remind).putBoolean("large_text",largeText)
        .putBoolean("reduced_motion",reducedMotion).commit()
    companion object{
        fun load(p:SharedPreferences)=ParentPreferences(
            p.getBoolean("voice",true),p.getBoolean("english",true),p.getBoolean("music",false),p.getBoolean("effects",true),
            p.getInt("speech_volume",85).coerceIn(0,100),p.getInt("music_volume",20).coerceIn(0,100),
            p.getInt("effect_volume",40).coerceIn(0,100),p.getInt("speed_index",2).coerceIn(0,4),
            p.getInt("study_minutes",20).coerceIn(5,60),p.getBoolean("remind_rest",true),
            p.getBoolean("large_text",false),p.getBoolean("reduced_motion",false)
        )
    }
}

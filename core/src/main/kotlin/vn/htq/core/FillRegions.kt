package vn.htq.core

/** Bounded fill based on source outline, not an answer polygon. Outside/background is not fillable. */
object FillRegions {
    fun boundedMask(argb:IntArray,width:Int,height:Int,x:Int,y:Int,threshold:Int=160,dilation:Int=1):BooleanArray? {
        require(width in 1..1280&&height in 1..1280&&argb.size==width*height&&threshold in 0..255&&dilation in 0..2)
        if(x !in 0 until width||y !in 0 until height)return null
        fun channel(v:Int,shift:Int,a:Int)=(((v ushr shift) and 255)*a+255*(255-a))/255
        val edge=BooleanArray(argb.size){i->val v=argb[i];val a=v ushr 24;minOf(channel(v,16,a),channel(v,8,a),channel(v,0,a))<threshold}
        val blocked=BooleanArray(edge.size){i->val xx=i%width;val yy=i/width;(-dilation..dilation).any{dy->(-dilation..dilation).any{dx->val nx=xx+dx;val ny=yy+dy;nx in 0 until width&&ny in 0 until height&&edge[ny*width+nx]}}}
        val seed=y*width+x;if(blocked[seed])return null
        val mask=BooleanArray(argb.size);val queue=IntArray(argb.size);var head=0;var tail=0
        fun add(i:Int){if(!blocked[i]&&!mask[i]){mask[i]=true;queue[tail++]=i}}
        add(seed)
        while(head<tail){val i=queue[head++];val xx=i%width;val yy=i/width;if(xx==0||yy==0||xx==width-1||yy==height-1)return null;add(i-1);add(i+1);add(i-width);add(i+width)}
        return mask
    }
    fun rainbowBand(center:List<Float>,radii:List<Float>,x:Float,y:Float):Int? {
        if(center.size!=2||radii.size<2||!x.isFinite()||!y.isFinite()||y>center[1])return null
        val radius=kotlin.math.hypot(x-center[0],y-center[1]);return (0 until radii.lastIndex).firstOrNull{radius<=radii[it]&&radius>=radii[it+1]}
    }
}

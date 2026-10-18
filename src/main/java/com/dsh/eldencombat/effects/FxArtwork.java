package com.dsh.eldencombat.effects;

import com.google.gson.JsonObject;
import java.util.List;

/** Immutable authored geometry. Dimensions are visual blocks, never collision parameters. */
public record FxArtwork(float width, float height, float anchorU, float anchorV,
                        float x, float y, float z, boolean horizontal,
                        List<Float> rolls, List<Float> scales) {
    public FxArtwork {
        rolls = List.copyOf(rolls); scales = List.copyOf(scales);
        for (float n : new float[]{width,height,anchorU,anchorV,x,y,z})
            if (!Float.isFinite(n)) throw new IllegalArgumentException("Nonfinite artwork geometry");
        if (width <= 0 || height <= 0 || width > 32 || height > 32
                || anchorU < 0 || anchorU > 1 || anchorV < 0 || anchorV > 1
                || rolls.isEmpty() || scales.isEmpty()) throw new IllegalArgumentException("Invalid artwork bounds");
        for(float r:rolls) if(!Float.isFinite(r)) throw new IllegalArgumentException("Invalid artwork roll");
        for(float s:scales) if(!Float.isFinite(s)||s<=0||s>2) throw new IllegalArgumentException("Invalid artwork scale");
    }
    public float roll(int phase) { return rolls.get(Math.floorMod(phase, rolls.size())); }
    public float scale(int phase) { return scales.get(Math.floorMod(phase, scales.size())); }
    /**
     * 一条 lane 的平面几何。
     *
     * <p>{@code id} 是**原始** lane 编号（0..15）。列表本身已按 blocked 过滤，所以下标不能当 lane ID 用：
     * 客户端遮挡求解、共享 mask 与诊断都要靠这个字段把"过滤后的序号"还原成原始编号。
     */
    public record Lane(int id, float center, float width, float forward, float u0, float u1) {}
    public static List<Lane> lanes(float radius,float travel,int blocked,boolean flame) {
        if(!Float.isFinite(radius)||radius<=0||!Float.isFinite(travel))return List.of();
        var lanes=new java.util.ArrayList<Lane>(WaveFront.LANES);
        for(int i=0;i<WaveFront.LANES;i++) {
            if((blocked&(1<<i))!=0)continue;
            lanes.add(new Lane(i,(float)WaveFront.laneCenter(i)*radius,2*radius/WaveFront.LANES,
                    flame?0:-(float)WaveFront.laneLag(i,travel),i/(float)WaveFront.LANES,(i+1f)/WaveFront.LANES));
        }
        return List.copyOf(lanes);
    }
    public static FxArtwork parse(JsonObject json) {
        var size=json.getAsJsonArray("size");var anchor=json.getAsJsonArray("anchor");var offset=json.getAsJsonArray("offset");
        return new FxArtwork(size.get(0).getAsFloat(),size.get(1).getAsFloat(),anchor.get(0).getAsFloat(),anchor.get(1).getAsFloat(),
                offset.get(0).getAsFloat(),offset.get(1).getAsFloat(),offset.get(2).getAsFloat(),json.get("horizontal").getAsBoolean(),
                json.getAsJsonArray("rolls").asList().stream().map(v->v.getAsFloat()).toList(),
                json.getAsJsonArray("scales").asList().stream().map(v->v.getAsFloat()).toList());
    }
    /** Every declared frame is reachable; the renderer does not quantize through sixteen steps. */
    public static int frame(float progress, int count) {
        if(count<1||count>64) throw new IllegalArgumentException("Invalid frame count");
        if(!Float.isFinite(progress)||progress<0||progress>=1) return -1;
        return Math.min(count-1,(int)(progress*count));
    }
}

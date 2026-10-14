package com.dsh.eldencombat.skeleton;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class SkinLayout {
    private static final String[] FACES = {"east", "west", "up", "down", "south", "north"};
    private final Map<String, int[]> faces = new HashMap<>();
    private final Map<String, int[]> overlays = new HashMap<>();
    private final Set<String> internal = new HashSet<>();

    public SkinLayout(JsonObject data) {
        read(data.getAsJsonObject("faces"), faces);
        read(data.getAsJsonObject("overlayFaces"), overlays);
        for (JsonElement value : data.getAsJsonArray("internalJointFaces")) internal.add(value.getAsString());
    }

    private static void read(JsonObject data, Map<String, int[]> target) {
        data.asMap().forEach((cube, entry) -> entry.getAsJsonObject().asMap().forEach((face, rect) -> {
            int[] bounds = new int[4];
            for (int i = 0; i < 4; i++) bounds[i] = rect.getAsJsonArray().get(i).getAsInt();
            target.put(cube + "." + face, bounds);
        }));
    }

    public float[] uv(String cube, int face, boolean overlay) {
        int[] rect = (overlay ? overlays : faces).get(cube + "." + FACES[face]);
        if (rect == null) throw new IllegalArgumentException("Missing skin UV: " + cube);
        return uv(rect, 64, 64);
    }

    public boolean internal(String cube, int face) { return internal.contains(cube + "." + FACES[face]); }

    /** Match vanilla ModelPart's UV orientation after converting the authored player axes. */
    public float[] minecraftUv(String cube,int face,boolean overlay) {
        return orient(uv(cube,face==0?1:face==1?0:face,overlay),face);
    }

    private static float[] orient(float[] uv,int face) {
        if(face==0||face==1||face==4||face==5) {
            float sum=uv[0]+uv[2];
            for(int i=0;i<4;i++)uv[i*2]=sum-uv[i*2];
        } else if(face==2) {
            float sum=uv[1]+uv[5];
            for(int i=0;i<4;i++)uv[i*2+1]=sum-uv[i*2+1];
        }
        return uv;
    }

    public static float[] uv(int[] rect, int width, int height) {
        float u0 = (rect[0] + 0.5f) / width, v0 = (rect[1] + 0.5f) / height;
        float u1 = (rect[2] - 0.5f) / width, v1 = (rect[3] - 0.5f) / height;
        return new float[]{u0, v0, u1, v0, u0, v1, u1, v1};
    }

    public static float[] armorUv(ModelData.CubeSpec cube, int face) {
        String name = cube.name();
        int x = name.equals("head") ? 0 : name.equals("body") ? 16 : name.startsWith("arm_") ? 40 : 0;
        int y = name.equals("head") ? 0 : 16;
        int w = name.equals("head") ? 8 : name.equals("body") ? 8 : 4;
        int d = name.equals("head") ? 8 : 4, h = name.equals("head") ? 8 : 12;
        int start = name.endsWith("_lower") ? 6 : 0, end = name.endsWith("_upper") ? 6 : h;
        int[][] rectangles = {{x, y+d+start, x+d, y+d+end}, {x+d+w, y+d+start, x+2*d+w, y+d+end},
            {x+d,y,x+d+w,y+d}, {x+d+w,y,x+d+2*w,y+d},
            {x+2*d+w,y+d+start,x+2*d+2*w,y+d+end}, {x+d,y+d+start,x+d+w,y+d+end}};
        return orient(uv(rectangles[face==0?1:face==1?0:face],64,32),face);
    }
}

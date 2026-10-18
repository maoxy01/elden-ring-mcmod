package com.dsh.eldencombat.client;

import com.dsh.eldencombat.effects.FxSlashRoute;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import java.util.*;

/**
 * Validate all data at reload time; rendering uses immutable parsed IDs only.
 *
 * <p>Two shapes coexist on purpose. {@code fx.slash} stays the shared legacy entry for every effect that
 * has not been given its own artwork, and {@code fx.routes.<id>} describes one effect family: its own
 * frames, its own plane and its own degraded silhouette. A pending route keeps the legacy artwork, so
 * declaring a route never changes what the game draws until that route is switched to ready.
 */
public final class PresentationRoutes extends SimplePreparableReloadListener<PresentationRoutes.Data> {
    /** One effect route: the frames it declares and the rules it was authored with. */
    public record Route(List<ResourceLocation> frames, FxSlashRoute spec, com.dsh.eldencombat.effects.FxArtwork artwork) {
        public Route { frames = List.copyOf(frames); }
    }
    /** Icons, per-effect routes, and the legacy shared slash frames the untouched kinds still sample. */
    public record Data(Map<String,ResourceLocation> icons, Map<String,Route> routes, List<ResourceLocation> frames) {
        public Data {
            icons = Map.copyOf(icons); routes = Map.copyOf(routes); frames = List.copyOf(frames);
        }
    }
    private static volatile Data current = new Data(Map.of(), Map.of(), List.of());
    private static volatile long revision;
    private static final Set<String> tracedIcons = new HashSet<>();
    private static ResourceLocation id(String value){
        if(!value.contains(":"))throw new IllegalArgumentException("Resource namespace missing: "+value);
        return ResourceLocation.parse(value);
    }
    private static String status(JsonObject entry){
        String status=entry.get("status").getAsString();
        if(!Set.of("ready","existing","pending").contains(status))throw new IllegalArgumentException("Invalid resource status");
        return status;
    }
    private static ResourceLocation particleTexture(ResourceLocation frame){
        return ResourceLocation.fromNamespaceAndPath(frame.getNamespace(),"textures/particle/"+frame.getPath()+".png");
    }
    @Override protected Data prepare(ResourceManager manager,ProfilerFiller profiler){
        try(var reader=manager.getResourceOrThrow(ResourceLocation.parse("eldencombat:routes/2d.json")).openAsReader()){
            var data=JsonParser.parseReader(reader).getAsJsonObject();
            var icons=new HashMap<String,ResourceLocation>();
            for(var value:data.getAsJsonObject("ui").entrySet()){
                var entry=value.getValue().getAsJsonObject();var state=status(entry);
                var texture=id(entry.get("texture").getAsString());
                if(!state.equals("pending")){
                    if(manager.getResource(texture).isEmpty())
                        com.dsh.eldencombat.EldenCombat.LOGGER.warn("Missing UI texture {}; text fallback retained",texture);
                    else icons.put(value.getKey(),texture);
                }
            }
            var fx=data.getAsJsonObject("fx");
            var slash=fx.getAsJsonObject("slash");var slashState=status(slash);
            if(!id(slash.get("atlas").getAsString()).equals(TextureAtlas.LOCATION_PARTICLES))throw new IllegalArgumentException("Unsupported slash atlas");
            var slashFrames=new ArrayList<ResourceLocation>();var values=slash.getAsJsonArray("frames");
            if(values.size()!=16)throw new IllegalArgumentException("Slash requires 16 frames, indices 0..15");
            for(var value:values){
                var frame=id(value.getAsString());
                if(!slashState.equals("pending")){
                    var png=particleTexture(frame);
                    if(manager.getResource(png).isEmpty())throw new IllegalArgumentException("Missing particle texture: "+png);
                }
                slashFrames.add(frame);
            }
            var routes=new HashMap<String,Route>();
            if(fx.has("routes"))for(var value:fx.getAsJsonObject("routes").entrySet()){
                String key=value.getKey();
                if(!key.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("Invalid FX route id: "+key);
                var defaults=FxSlashRoute.defaultsForRoute(key);
                if(defaults==null)throw new IllegalArgumentException("Unknown FX route: "+key);
                var entry=value.getValue().getAsJsonObject();var state=status(entry);
                if(entry.has("atlas")&&!id(entry.get("atlas").getAsString()).equals(TextureAtlas.LOCATION_PARTICLES))
                    throw new IllegalArgumentException("Unsupported FX route atlas: "+key);
                boolean ready=!state.equals("pending");
                var frames=new ArrayList<ResourceLocation>();
                if(entry.has("frames")){
                    var routeFrames=entry.getAsJsonArray("frames");
                    if(routeFrames.size()<1||routeFrames.size()>FxSlashRoute.MAX_FRAMES)
                        throw new IllegalArgumentException("FX route frame count: "+key);
                    for(var frame:routeFrames){
                        var parsed=id(frame.getAsString());
                        if(ready){
                            var png=particleTexture(parsed);
                            // The particles atlas lists every namespace's textures/particle through the vanilla
                            // directory source, so the frame just has to exist as a PNG; the client additionally
                            // checks that the sprite really landed in the stitched atlas before it is drawn.
                            if(manager.getResource(png).isEmpty())com.dsh.eldencombat.EldenCombat.LOGGER.warn("[skill-fx] missing PNG {}; route {} retains its outline fallback",png,key);
                        }
                        frames.add(parsed);
                    }
                }else if(ready)throw new IllegalArgumentException("FX route declares no frames: "+key);
                routes.put(key,new Route(frames,FxSlashRoute.parse(entry,defaults.withFrames(Math.max(1,frames.size())).withReady(ready)),
                        entry.has("artwork") ? com.dsh.eldencombat.effects.FxArtwork.parse(entry.getAsJsonObject("artwork")) : null));
            }
            return new Data(icons,routes,slashFrames);
        }catch(Exception error){com.dsh.eldencombat.EldenCombat.LOGGER.error("2D manifest rejected; retaining previous routes",error);return current;}
    }
    @Override protected void apply(Data data,ResourceManager manager,ProfilerFiller profiler){current=data;revision++;}
    /** Reload counter; client caches keyed on it must recompute after every accepted manifest. */
    public static long revision(){return revision;}
    /** Declared route, or null when the manifest does not describe that effect family. */
    public static Route route(String routeId){return routeId==null?null:current.routes().get(routeId);}
    public static FxSlashRoute routeSpec(String routeId){var route=route(routeId);return route==null?null:route.spec();}
    public static List<ResourceLocation> routeFrames(String routeId){var route=route(routeId);return route==null?List.of():route.frames();}
    public static ResourceLocation slashFrame(int index,ResourceLocation fallback){
        var frames=current.frames();if(index<0||index>=frames.size())return fallback;
        var candidate=frames.get(index);
        var texture=Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        return texture instanceof TextureAtlas atlas&&atlas.getSprite(candidate).contents().name().equals(candidate)?candidate:fallback;
    }
    /** 显式诊断收集到的图标轨迹（{@code key|texture}）；仅用于验收核对实际 16/32 源选择。 */
    public static Set<String> tracedIcons(){return Set.copyOf(tracedIcons);}
    public static boolean drawIcon(GuiGraphics graphics,String key,int x,int y){
        var texture=current.icons().get(key);
        int pixels=16;
        if(texture==null||Minecraft.getInstance().getWindow().getGuiScale()>=2){
            var larger=current.icons().get(key+"_32");
            if(larger!=null){texture=larger;pixels=32;}
        }
        if(texture==null)return false;
        graphics.blit(texture,x,y,16,16,0,0,pixels,pixels,pixels,pixels);
        if(Boolean.getBoolean("eldencombat.artAcceptance")&&tracedIcons.add(key+"|"+texture))
            com.dsh.eldencombat.EldenCombat.LOGGER.info("ART_UI_ICON: key={} texture={} source={} target=16 x={} y={} guiScale={}",
                    key,texture,pixels,x,y,Minecraft.getInstance().getWindow().getGuiScale());
        return true;
    }
}

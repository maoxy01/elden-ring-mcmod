package com.dsh.eldencombat.client.grace;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.network.GraceNodesPayload;
import com.dsh.eldencombat.network.GraceTravelPayload;
import journeymap.api.v2.client.*;
import journeymap.api.v2.client.display.*;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.util.UIState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Public JourneyMap API only; its lifecycle owns rendering, hover and popup hit testing. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class GraceMapPlugin implements IClientPlugin {
    private static IClientAPI api;
    private static GraceNodesPayload snapshot=new GraceNodesPayload(java.util.List.of());
    private static final Map<UUID,MarkerOverlay> markers=new HashMap<>();
    /** 是否已经接管地图显示（= 收到过服务端快照，说明服务端赐福适配器已装载）。 */
    private static boolean claimed;
    @Override public String getModId(){return EldenCombat.MODID;}
    @Override public void initialize(IClientAPI clientAPI){
        api=clientAPI;
        // 服务端没装载赐福时永远不会有节点包；此时不要碰 Waystones 自己的地图点。
        if(!snapshot.nodes().isEmpty())replace(snapshot);
        EldenCombat.LOGGER.info("GRACE_MAP_PLUGIN_READY: public API 2.0.0");
    }
    public static void replace(GraceNodesPayload payload){
        if(api!=null&&snapshot.equals(payload)&&markers.size()==payload.nodes().size())return;
        snapshot=payload;if(api==null)return;
        api.removeAll(EldenCombat.MODID);markers.clear();
        if(!claimed){
            // 赐福接管地图显示（否则会与 Waystones 的原生图标重叠）。只在**收到过服务端快照**之后
            // 清理 Waystones 的插件点，免得没启用赐福的存档被客户端顺手清掉。
            claimed=true;
            api.removeAllWaypoints("waystones");
        }
        EldenCombat.LOGGER.info("GRACE_MAP_SNAPSHOT: {} authorized nodes",payload.nodes().size());
        for(var n:payload.nodes()){
            var image=new MapImage(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,
                    "textures/map/grace_"+n.origin()+"_"+n.state()+".png"),16,16).centerAnchors().setBlur(false);
            var marker=new MarkerOverlay(EldenCombat.MODID,n.pos(),image);
            marker.setDimension(ResourceKey.create(Registries.DIMENSION,n.dimension())).setTitle(n.name())
                    .setOverlayGroupName("赐福").setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap);
            marker.setOverlayListener(new IOverlayListener(){
                @Override public void onOverlayMenuPopup(UIState state,Point2D.Double mouse,BlockPos pos,ModPopupMenu menu){
                    menu.addMenuItem("前往赐福："+n.name(),ignored->request(n.uid()));
                }
            });
            try{api.show(marker);markers.put(n.uid(),marker);}catch(Exception failure){EldenCombat.LOGGER.error("Grace map marker failed",failure);}
        }
    }
    public static void request(UUID uid){
        if(Minecraft.getInstance().getConnection()==null)return;
        // 序号与菜单共用同一个计数器：服务端用它挡重复旅行请求，两边各数一套会互相把对方判成重放。
        int sequence=GraceClientActions.nextSequence();
        PacketDistributor.sendToServer(new GraceTravelPayload(uid,sequence));
        EldenCombat.LOGGER.info("GRACE_MAP_REQUEST: uid={} sequence={}",uid,sequence);
    }
    public static Map<UUID,MarkerOverlay> markers(){return Map.copyOf(markers);}
}

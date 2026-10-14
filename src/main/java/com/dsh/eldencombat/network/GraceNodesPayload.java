package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/** Per player replacement snapshot; undiscovered/private nodes are omitted by the server. */
public record GraceNodesPayload(List<Node> nodes) implements CustomPacketPayload {
    public record Node(UUID uid,String name,ResourceLocation dimension,BlockPos pos,String origin,String state) {}
    public static final Type<GraceNodesPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"grace_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GraceNodesPayload> STREAM_CODEC=StreamCodec.of(
        (buf,value)->{
            buf.writeVarInt(value.nodes.size());
            for(var n:value.nodes){buf.writeUUID(n.uid);buf.writeUtf(n.name,256);buf.writeResourceLocation(n.dimension);buf.writeBlockPos(n.pos);buf.writeUtf(n.origin,16);buf.writeUtf(n.state,16);}
        },buf->{
            int count=buf.readVarInt();if(count<0||count>512)throw new IllegalArgumentException("Invalid grace count");
            var nodes=new ArrayList<Node>(count);
            for(int i=0;i<count;i++)nodes.add(new Node(buf.readUUID(),buf.readUtf(256),buf.readResourceLocation(),buf.readBlockPos(),buf.readUtf(16),buf.readUtf(16)));
            return new GraceNodesPayload(List.copyOf(nodes));
        });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

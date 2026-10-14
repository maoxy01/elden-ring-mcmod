const keys=values=>values.map((value,i)=>({tick:i*10,value}));
export const bowAnimations={schemaVersion:1,ticksPerSecond:20,rotationOrder:'XYZ',rotationMode:'additive_degrees',clips:[{
  id:'bow_draw',lengthTicks:20,loop:false,tracks:{bow_upper:keys([[0,0,0],[0,0,-3],[0,0,-6]]),bow_lower:keys([[0,0,0],[0,0,3],[0,0,6]])}
}]};
export const bowDrawData={schemaVersion:1,id:'eldencombat:long_bow',clip:'bow_draw',ticksPerSecond:20,unitsPerBlock:16,
  origin:'main grip [0,0,0]; root does not move',
  string:{replaceRigidStringBoneGeometry:true,segmentThicknessModelUnits:1,
    upper:{bone:'bow_upper',pointRelativeToPivotModelUnits:[1.5,25.5,0]},
    lower:{bone:'bow_lower',pointRelativeToPivotModelUnits:[1.5,-25.5,0]},
    centerTrack:keys([[1.5,0,0],[4.5,0,0],[7.5,0,0]]),
    construction:'Transform endpoints by their limb bones, then draw upper -> center -> lower. Do not translate the entire rigid string.'},
  keyPoses:[{name:'rest',tick:0},{name:'half_draw',tick:10},{name:'full_draw',tick:20}],
  playback:'Art reference duration. Runtime should sample tick = clamp(drawProgress,0,1)*20 and hold at 20; release resets to rest.',
  runtimeIntegrated:false,inGameAccepted:false};

# 长弓动态消费所需美术数据（2026-10-13）

当前 long_bow 的 root/bow_upper/bow_lower/string 骨骼已保留；没有长弓动画文件。WeaponAssets 会静态 bake，EquipmentRenderer 的缓存网格不随原版 BowItem 蓄力改变，因此真实射箭通过也不能证明弦在动。

请美术提供 draw 0/0.5/1 三个关键状态的 bow_upper/bow_lower 旋转增量（度，XYZ）以及 string 位置增量（模型单位，16/格），root 保持握点不动；记录模型 SHA256 与弓梢/弦连接约束。消费者按原版 getTicksUsingItem / 20 归一化，使用 SkeletonPose.of(model, rotationDelta, positionDelta) 绘制活动骨骼，避免使用 root 静态网格。数值与造型由美术确定，战斗只消费与验证。

当前动态弓弦状态为未实现，不纳入实机通过项目。另需美术复核中盾 +Z 正面 / -Z 背面在手腕坐标中的朝向；掌柄交叠与朝向分别验收。

# 武器与人物比例草案

由 targets.json 生成；41 件独立配置。参考人偶身高 1.8 格，不把碰撞箱高度等同于游戏实际可见身高。神躯化剑有已查看的持握截图，其余 40 件是待校准的美术初值。不是官方或源模型实测数据。

目标长度 = 人物高度 × 比例；visualScale = 目标长度 / 当前模型静止 Y 跨度。原点、几何与图集均未改写。当前游戏尚未消费此表。

| 武器 | ID | 对应法环参考 | 武器/人高 | 目标格数 | 当前格数 | 网格倍率 | 依据状态 |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| 匕首 | `dagger` | Dagger | 0.28 | 0.504 | 1.1875 | 0.424421 | 待参考校准 |
| 直剑 | `straight_sword` | Longsword | 0.60 | 1.080 | 1.6875 | 0.640000 | 待参考校准 |
| 大剑 | `great_sword` | Claymore | 0.88 | 1.584 | 2.3750 | 0.666947 | 待参考校准 |
| 特大剑 | `colossal_sword` | Greatsword | 1.38 | 2.484 | 3.1250 | 0.794880 | 待参考校准 |
| 刺剑 | `thrusting_sword` | Estoc | 0.67 | 1.206 | 2.0625 | 0.584727 | 待参考校准 |
| 重刺剑 | `heavy_thrusting_sword` | Great Epee | 0.84 | 1.512 | 2.6875 | 0.562605 | 待参考校准 |
| 曲剑 | `curved_sword` | Scimitar | 0.61 | 1.098 | 1.8750 | 0.585600 | 待参考校准 |
| 大曲剑 | `curved_greatsword` | Dismounter | 0.97 | 1.746 | 2.7500 | 0.634909 | 待参考校准 |
| 刀 | `katana` | Uchigatana | 0.91 | 1.638 | 2.6250 | 0.624000 | 待参考校准 |
| 双头剑 | `twinblade` | Twinblade | 1.20 | 2.160 | 3.2500 | 0.664615 | 待参考校准 |
| 斧 | `axe` | Battle Axe | 0.48 | 0.864 | 1.6250 | 0.531692 | 待参考校准 |
| 大斧 | `greataxe` | Greataxe | 0.83 | 1.494 | 2.5000 | 0.597600 | 待参考校准 |
| 槌 | `hammer` | Mace | 0.46 | 0.828 | 1.5625 | 0.529920 | 待参考校准 |
| 大槌 | `great_hammer` | Great Mace | 0.86 | 1.548 | 2.5000 | 0.619200 | 待参考校准 |
| 特大武器 | `colossal_weapon` | Giant-Crusher | 1.20 | 2.160 | 3.0000 | 0.720000 | 待参考校准 |
| 连枷 | `flail` | Flail | 0.68 | 1.224 | 1.8750 | 0.652800 | 待参考校准 |
| 矛 | `spear` | Spear | 1.48 | 2.664 | 4.0000 | 0.666000 | 待参考校准 |
| 大矛 | `great_spear` | Lance | 1.70 | 3.060 | 4.2500 | 0.720000 | 待参考校准 |
| 戟 | `halberd` | Halberd | 1.53 | 2.754 | 3.7500 | 0.734400 | 待参考校准 |
| 镰刀 | `scythe` | Scythe | 1.40 | 2.520 | 3.0625 | 0.822857 | 待参考校准 |
| 软鞭 | `whip` | Whip (rest-pose silhouette, not uncoiled reach) | 1.52 | 2.736 | 2.6099 | 1.048318 | 待参考校准 |
| 拳套 | `fist` | Caestus | 0.17 | 0.306 | 0.6250 | 0.489600 | 待参考校准 |
| 钩爪 | `claw` | Hookclaws | 0.31 | 0.558 | 1.3125 | 0.425143 | 待参考校准 |
| 短弓 | `short_bow` | Shortbow | 0.66 | 1.188 | 2.3750 | 0.500211 | 待参考校准 |
| 长弓 | `long_bow` | Longbow | 1.02 | 1.836 | 3.6250 | 0.506483 | 待参考校准 |
| 大弓 | `great_bow` | Greatbow | 1.30 | 2.340 | 4.5000 | 0.520000 | 待参考校准 |
| 弩 | `crossbow` | Light Crossbow (stock to front) | 0.42 | 0.756 | 1.4375 | 0.525913 | 待参考校准 |
| 弩炮 | `ballista` | Hand Ballista (stock to front) | 0.80 | 1.440 | 2.0000 | 0.720000 | 待参考校准 |
| 辉石杖 | `glintstone_staff` | Astrologer's Staff | 1.10 | 1.980 | 3.3750 | 0.586667 | 待参考校准 |
| 圣印记 | `sacred_seal` | Finger Seal | 0.12 | 0.216 | 0.6875 | 0.314182 | 待参考校准 |
| 小盾 | `small_shield` | Buckler | 0.28 | 0.504 | 0.7500 | 0.672000 | 待参考校准 |
| 中盾 | `shield` | Brass Shield | 0.50 | 0.900 | 0.8750 | 1.028571 | 待参考校准 |
| 大盾 | `great_shield` | Lordsworn's Shield | 0.73 | 1.314 | 1.3750 | 0.955636 | 待参考校准 |
| 神躯化剑 | `sacred_relic_sword` | Sacred Relic Sword | 0.95 | 1.710 | 3.1875 | 0.536471 | 截图估算 |
| 玛莉卡的石槌 | `marikas_hammer` | Marika's Hammer | 0.58 | 1.044 | 1.9375 | 0.538839 | 待参考校准 |
| 玛利喀斯的黑剑 | `malikeths_black_blade` | Maliketh's Black Blade | 1.28 | 2.304 | 3.6250 | 0.635586 | 待参考校准 |
| 亵渎圣剑 | `blasphemous_blade` | Blasphemous Blade | 0.96 | 1.728 | 2.8750 | 0.601043 | 待参考校准 |
| 玛莲妮亚的义手刀 | `hand_of_malenia` | Hand of Malenia (player version) | 1.08 | 1.944 | 3.3750 | 0.576000 | 待参考校准 |
| 碎星大剑 | `starscourge_greatsword` | Starscourge Greatsword (one blade) | 1.10 | 1.980 | 3.1250 | 0.633600 | 待参考校准 |
| 蒙格温圣矛 | `mohgwyns_sacred_spear` | Mohgwyn's Sacred Spear | 1.75 | 3.150 | 3.7500 | 0.840000 | 待参考校准 |
| 蒙葛特咒剑 | `morgotts_cursed_sword` | Morgott's Cursed Sword | 1.16 | 2.088 | 3.2500 | 0.642462 | 待参考校准 |

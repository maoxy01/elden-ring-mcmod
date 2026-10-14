/**
 * Server-authoritative combat extension boundary.
 *
 * <p>Input flows from CombatIntentPayload through ServerCombat to SkillCasting.
 * SkillTransactions alone commits FP and stage changes for normal skill requests;
 * Runtime callbacks must not charge the same stage again. A rejected request must
 * not create a hit, projectile, cooldown or presentation acceptance.
 *
 * <p>SkillDriver.Runtime is the existing extension interface. Register implementations
 * through SkillDriver; do not introduce a parallel tick loop. onActive owns server
 * effects; SkillCastState owns per-stage/per-channel hit deduplication. onCancel
 * removes cast-owned effects, while onDetach also clears player-scoped transient
 * motion/landing state. Cleanup must tolerate repeated calls.
 *
 * <p>SkillServerEvents publishes private FP to its owner and cast presentation to
 * tracking clients. Client visuals never authorize damage. FP and weapon progression
 * use existing codecs/components; live cast state is transient. See
 * docs/DEEPSEEK-COMBAT-FRAMEWORK-HANDOFF-20261013.md for scoped implementation work.
 */
package com.dsh.eldencombat.server.skills;

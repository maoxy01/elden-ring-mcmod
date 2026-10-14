const fs=require('fs');
const p='src/test/java/com/dsh/eldencombat/FxSlashRouteTest.java';
let s=fs.readFileSync(p,'utf8');
s=s.replace(/            \/\/ An unknown kind[\s\S]*?assertNull\(FxSlashRoute.routeId\(kind\)\);/, '            assertNotNull(FxSlashRoute.routeId(kind));');
s=s.replace('assertEquals(4, routed);','assertEquals(10, routed);').replace('assertNull(FxSlashRoute.defaults(Kind.BLOOD_RITUAL));','assertNotNull(FxSlashRoute.defaults(Kind.BLOOD_RITUAL));');
s=s.replace('Path.of("src/main/resources/assets",', 'Path.of("build/resources/main/assets",');
fs.writeFileSync(p,s);

package kr.minecraft.survival.economy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClaimGeometryTest {
 @Test void intersectsSurroundingClaimsAndNegativeChunkEdges(){assertTrue(ClaimBridge.intersects(-100,-100,100,100,0,0));assertTrue(ClaimBridge.intersects(15,15,16,16,0,0));assertTrue(ClaimBridge.intersects(15,15,16,16,1,1));assertFalse(ClaimBridge.intersects(16,0,100,15,0,0));assertTrue(ClaimBridge.intersects(-16,-16,-1,-1,-1,-1));assertFalse(ClaimBridge.intersects(0,0,15,15,-1,-1));assertFalse(ClaimBridge.intersects(-100,-100,100,100,Integer.MAX_VALUE,Integer.MIN_VALUE));}
}

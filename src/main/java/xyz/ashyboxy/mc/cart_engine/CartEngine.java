package xyz.ashyboxy.mc.cart_engine;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartEngine implements ModInitializer {
	public static final String MOD_ID = "cart_engine";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cart Engine");

	// TODO: mod support/refactor
	// these values could potentially be changed to be used as multipliers rather than hard values
	// for example, max speed could be swapped for 4.25 (34/8=4.25)
	// TODO: mod support
	// vanillaMaxSpeed should just be obtained from the minecart itself
	// this would also fix the clamping issues during acceleration
	// TODO: refactor
	// other constants and magic numbers should be moved here
	public static final double tps = 20;
	public static final double maxSpeed = 34 / tps;
	public static final double maxMomentum = maxSpeed * 5;
	public static final double vanillaMaxSpeed = 8 / tps;
	public static final double vanillaMaxMomentum = 40 / tps;

	@Override
	public void onInitialize() {
		LOGGER.info("Unfortunately not including railguns");
	}
}

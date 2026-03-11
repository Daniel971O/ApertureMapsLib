package com.aperturemapslib;

import com.aperturemapslib.core.StitcherHolderTransformer;
import com.aperturemapslib.proxy.CommonProxy;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = ApertureMapsLib.MODID,
        name = ApertureMapsLib.NAME,
        version = ApertureMapsLib.VERSION,
        dependencies = "after:ctm"
)
public class ApertureMapsLib {

    public static final String MODID = "aperturemapslib";
    public static final String NAME = "ApertureMapsLib";
    public static final String VERSION = "1.0.0";

    private static final Logger LOGGER = LogManager.getLogger("ApertureMapsLib");

    @SidedProxy(
            clientSide = "com.aperturemapslib.proxy.ClientProxy",
            serverSide = "com.aperturemapslib.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        boolean ctmLoaded = Loader.isModLoaded("ctm");
        if (!ctmLoaded) {
            if (StitcherHolderTransformer.isCtmRequired()) {
                LOGGER.error("CTM not found. The library requires CTM. "
                        + "You can disable this requirement in config/aperturemapslib.cfg: dependency.ctm.required=false");
                proxy.onMissingCtmRequired();
            } else {
                LOGGER.warn("CTM not found. Continuing because dependency.ctm.required=false in config/aperturemapslib.cfg");
            }
        }
        proxy.preInit(event);
    }
}

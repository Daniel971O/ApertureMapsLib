package com.aperturemapslib.proxy;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void onMissingCtmRequired() {
        throw new RuntimeException("CTM not found. Set dependency.ctm.required=false in config/aperturemapslib.cfg to allow running without CTM.");
    }
}

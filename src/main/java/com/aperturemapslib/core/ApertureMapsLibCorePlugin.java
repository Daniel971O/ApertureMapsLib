package com.aperturemapslib.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.Name("ApertureMapsLibCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"com.aperturemapslib.core"})
public class ApertureMapsLibCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "com.aperturemapslib.core.TextureAtlasAspectRatioTransformer",
                "com.aperturemapslib.core.StitcherHolderTransformer",
                "com.aperturemapslib.core.CTMSubmapTransformer",
                "com.aperturemapslib.core.CTMPatternTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

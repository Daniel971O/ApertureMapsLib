package com.aperturemapslib.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;

public class GuiMissingCtmScreen extends GuiScreen {
    private List<String> lines;

    private static final String TITLE = "ApertureMapsLib";
    private static final String MESSAGE = "Мод ctm (CTM-MC1.12.2-1.0.2.31.jar) отсутствует. Он обязателен для ApertureMapsLib."
            + "Чтобы исправить проблему скачайте (CTM-MC1.12.2-1.0.2.31.jar) или отключите обязательность в конфиге мода: config/aperturemapslib.cfg -> dependency.ctm.required=false";

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 40, 200, 20, "Выход"));
        this.lines = this.fontRenderer.listFormattedStringToWidth(MESSAGE, this.width - 40);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            this.mc.shutdown();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, TITLE, this.width / 2, 20, 0xFFFFFF);
        int y = 50;
        if (lines != null) {
            for (String line : lines) {
                this.drawString(this.fontRenderer, line, 20, y, 0xE0E0E0);
                y += this.fontRenderer.FONT_HEIGHT + 2;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

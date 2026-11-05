package pl.example.tracers;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class TracersClient implements ClientModInitializer {

    private static final double MAX_DISTANCE = 256.0;

    private static final int COLOR_PLAYER = 0xFFFF3030; // czerwony
    private static final int COLOR_ANIMAL = 0xFF30FF30; // zielony

    private static KeyBinding toggleKey;
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracers.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.tracers"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Tracery: " + (enabled ? "włączone" : "wyłączone")), true);
                }
            }
        });

        WorldRenderEvents.LAST.register(TracersClient::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!enabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        float tickDelta = ctx.tickCounter().getTickDelta(true);
        Vec3d camPos = ctx.camera().getPos();
        // Punkt startowy: tuż przed kamerą, czyli w miejscu celownika
        Vec3d start = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw()).multiply(0.5);
        Matrix4f matrix = ctx.matrixStack().peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.disableDepthTest(); // linie widoczne przez ściany
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean any = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            int color;
            if (entity instanceof PlayerEntity) {
                color = COLOR_PLAYER;
            } else if (entity instanceof AnimalEntity) {
                color = COLOR_ANIMAL;
            } else {
                continue;
            }

            if (entity.squaredDistanceTo(mc.player) > MAX_DISTANCE * MAX_DISTANCE) continue;

            Vec3d target = entity.getLerpedPos(tickDelta)
                    .add(0, entity.getHeight() / 2.0, 0)
                    .subtract(camPos);

            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(color);
            buffer.vertex(matrix, (float) target.x, (float) target.y, (float) target.z).color(color);
            any = true;
        }

        if (any) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}

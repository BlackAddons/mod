package org.blackaddons.platform.mixin.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.serialization.JsonOps;
import kotlin.text.Regex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.FormattedCharSequence;
import org.blackaddons.api.chat.ChatLine;
import org.blackaddons.api.chat.ChatMessage;
import org.blackaddons.api.chat.ChatMotions;
import org.blackaddons.api.chat.ChatRetentions;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.chat.ChatLineAddedEvent;
import org.blackaddons.api.event.chat.ChatMessageEvent;
import org.blackaddons.api.event.chat.ChatMessageType;
import org.blackaddons.platform.chat.ChatLines;
import org.blackaddons.platform.chat.StoredChatLine;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.blackaddons.platform.impl.chat.ChatComponentsKt;
import org.blackaddons.platform.impl.chat.MinecraftChat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;

/**
 * Chat addition is one private 4-arg `addMessage` with the source enum in its parameters, so we have the upstream
 * classification here.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatLines {

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private ArrayListDeque<String> recentChat;
    @Shadow private int chatScrollbarPos;
    @Shadow public abstract void scrollChat(int dir);
    @Shadow public abstract void resetChatScroll();
    @Shadow public abstract boolean isChatFocused();
    @Shadow protected abstract int getWidth();
    @Shadow protected abstract double getScale();
    @Shadow public abstract int getLinesPerPage();
    @Shadow protected abstract int getLineHeight();

    @Unique private List<GuiMessage> blackaddons$keptMessages;
    @Unique private List<String> blackaddons$keptSent;
    @Unique private boolean blackaddons$extraLine;
    @Unique private String blackaddons$pendingRaw;
    @Unique private Component blackaddons$pendingShown;
    @Unique private long blackaddons$newestAt;
    @Unique private long blackaddons$lastFrame;
    @Unique private double blackaddons$drawnScroll;

    /** Far enough in the past that a restored line is already faded out, without overflowing the age. */
    @Unique private static final int blackaddons$RESTORED_AT = Integer.MIN_VALUE / 2;

    /**
     * Vanilla's hit test is inside the render walk, so this repeats it: undo the chat scale, drop the 4px inset, and
     * measure up past the 40px input box.
     */
    @Override
    public ChatLine blackaddonsLineAt(double mouseX, double mouseY) {
        if (!isChatFocused()) return null;
        double scale = getScale();
        if (scale <= 0.0) return null;

        double x = mouseX / scale - 4.0;
        if (x < 0.0 || x > Math.floor(getWidth() / scale)) return null;

        double bottom = Minecraft.getInstance().getWindow().getGuiScaledHeight() - mouseY - 40.0;
        double line = bottom / (scale * getLineHeight());
        if (line < 0.0 || line >= Math.min(getLinesPerPage(), trimmedMessages.size())) return null;

        int index = (int) Math.floor(line) + chatScrollbarPos;
        if (index < 0 || index >= trimmedMessages.size()) return null;
        return (ChatLine) (Object) trimmedMessages.get(index).parent();
    }

    @Override
    public GuiMessage blackaddonsNewestMessage() {
        return allMessages.isEmpty() ? null : allMessages.getFirst();
    }

    /**
     * Swaps a stored message's content, like vanilla does for moderator deletes. {@code GuiMessage} is a record, so the
     * copy keeps age (drives the fade and the 3s delete guard), signature, source and tag.
     */
    @Override
    public GuiMessage blackaddonsReplaceMessage(GuiMessage original, Component replacement) {
        ListIterator<GuiMessage> iterator = allMessages.listIterator();
        while (iterator.hasNext()) {
            GuiMessage message = iterator.next();
            if (message != original) continue;
            GuiMessage revised = new GuiMessage(
                    message.addedTime(), replacement, message.signature(), message.source(), message.tag());
            iterator.set(revised);
            ((StoredChatLine) (Object) message).blackaddonsCopyStateTo((StoredChatLine) (Object) revised);
            /**
             * Rebuilding re-wraps every message, and wrapping scrolls the chat once per line. Hold the position,
             * scrollChat(0) clamps if the replacement wrapped shorter.
             */
            int scroll = chatScrollbarPos;
            blackaddons$refreshTrimmedMessages();
            chatScrollbarPos = scroll;
            scrollChat(0);
            return revised;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatLine> blackaddonsLines() {
        return (List<ChatLine>) (List<?>) allMessages;
    }

    @Override
    public void blackaddonsRemoveMessage(GuiMessage message) {
        if (!allMessages.removeIf(stored -> stored == message)) return;
        for (int i = trimmedMessages.size() - 1; i >= 0; i--) {
            if (trimmedMessages.get(i).parent() != message) continue;
            trimmedMessages.remove(i);
            if (i < chatScrollbarPos) chatScrollbarPos--;
        }
        scrollChat(0);
    }

    /**
     * Plain {@link JsonOps} without registries, since restore runs before the server sends them. Messages that need
     * them (item hovers) get saved as raw text.
     */
    @Override
    public List<String> blackaddonsSavedMessages() {
        List<String> saved = new ArrayList<>(allMessages.size());
        for (GuiMessage message : allMessages) {
            String raw = ((ChatLine) (Object) message).getRaw();
            JsonObject json = new JsonObject();
            json.addProperty("raw", raw);
            json.addProperty("source", message.source().name());
            json.add("content", ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, message.content())
                .result().orElseGet(() -> new JsonPrimitive(raw)));
            saved.add(json.toString());
        }
        return saved;
    }

    @Unique
    private void blackaddons$restoreSaved() {
        List<String> saved = ChatRetentions.INSTANCE.restoreMessages();
        if (saved.isEmpty()) return;
        for (String line : saved) {
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                Component content = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json.get("content")).getOrThrow();
                GuiMessageSource source = GuiMessageSource.valueOf(json.get("source").getAsString());
                GuiMessage message = new GuiMessage(blackaddons$RESTORED_AT, content, null, source, null);
                ((StoredChatLine) (Object) message).blackaddonsSetRaw(json.get("raw").getAsString());
                allMessages.add(message);
            } catch (RuntimeException unreadable) {
                continue;
            }
        }
        blackaddons$refreshTrimmedMessages();
    }

    @Override
    public void blackaddonsRefreshView() {
        blackaddons$refreshTrimmedMessages();
        resetChatScroll();
        blackaddons$drawnScroll = 0.0;
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void blackaddons$view(GuiMessage message, CallbackInfo ci) {
        /** Vanilla shows a message before queueing it, so the view needs its raw text, not cosmetic names. */
        if (message.content() == blackaddons$pendingShown) ((StoredChatLine) (Object) message).blackaddonsSetRaw(blackaddons$pendingRaw);
        if (!MinecraftChat.INSTANCE.shows((ChatLine) (Object) message)) ci.cancel();
    }

    @Invoker("refreshTrimmedMessages")
    abstract void blackaddons$refreshTrimmedMessages();

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blackaddons$addMessage(
            Component contents,
            MessageSignature signature,
            GuiMessageSource source,
            GuiMessageTag tag,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<Component> contentsRef
    ) {
        blackaddons$restoreSaved();
        String raw = contents.getString();
        String unformatted = ChatFormatting.stripFormatting(raw);
        ChatMessageType mappedType = switch (source) {
            case PLAYER -> ChatMessageType.PLAYER;
            case SYSTEM_CLIENT, SYSTEM_SERVER -> ChatMessageType.SYSTEM;
        };
        ChatMessageEvent event = new ChatMessageEvent(
                raw, unformatted != null ? unformatted : raw,
                mappedType, false
        );
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
            return;
        }

        Component shown = NameReplacer.INSTANCE.decorateChat(contents);
        ChatMessage suffix = event.getSuffix();
        if (suffix != null) shown = shown.copy().append(ChatComponentsKt.toComponent(suffix));
        contentsRef.set(shown);
        blackaddons$pendingRaw = raw;
        blackaddons$pendingShown = shown;
    }

    @Inject(method = "addMessageToQueue", at = @At("HEAD"))
    private void blackaddons$stored(GuiMessage message, CallbackInfo ci) {
        StoredChatLine line = (StoredChatLine) (Object) message;
        line.blackaddonsSetRaw(blackaddons$pendingRaw);
        blackaddons$pendingRaw = null;
        blackaddons$pendingShown = null;
        long now = System.nanoTime();
        line.setBlackaddonsAddedAt(now);
        if (MinecraftChat.INSTANCE.shows(line)) blackaddons$newestAt = now;
        EventBus.INSTANCE.post(new ChatLineAddedEvent(line));
    }

    @ModifyExpressionValue(
        method = {"addMessageToDisplayQueue", "addMessageToQueue", "addRecentChat"},
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int blackaddons$historyLength(int vanilla) {
        return ChatRetentions.INSTANCE.length(vanilla);
    }

    @Inject(method = "addRecentChat", at = @At("TAIL"))
    private void blackaddons$sent(String message, CallbackInfo ci) {
        ChatRetentions.INSTANCE.onSent(List.copyOf(recentChat));
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void blackaddons$keep(boolean history, CallbackInfo ci) {
        if (!history) return;
        blackaddons$keptMessages = ChatRetentions.INSTANCE.getKeepsMessagesOnDisconnect() ? List.copyOf(allMessages) : null;
        blackaddons$keptSent = ChatRetentions.INSTANCE.getKeepsSentOnDisconnect() ? List.copyOf(recentChat) : null;
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void blackaddons$restoreKept(boolean history, CallbackInfo ci) {
        if (blackaddons$keptMessages != null) {
            allMessages.addAll(blackaddons$keptMessages);
            blackaddons$refreshTrimmedMessages();
            blackaddons$keptMessages = null;
        }
        if (blackaddons$keptSent != null) {
            recentChat.clear();
            recentChat.addAll(blackaddons$keptSent);
            blackaddons$keptSent = null;
        }
    }

    @ModifyVariable(method = "forEachLine", at = @At("STORE"), ordinal = 0)
    private float blackaddons$fadeIn(float alpha, @Local GuiMessage.Line line) {
        long addedAt = ((StoredChatLine) (Object) line.parent()).getBlackaddonsAddedAt();
        return addedAt == 0L ? alpha : alpha * ChatMotions.INSTANCE.lineOpacity(blackaddons$msSince(addedAt));
    }

    @ModifyExpressionValue(
        method = "forEachLine",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I")
    )
    private int blackaddons$extraLine(int perPage) {
        return blackaddons$extraLine ? perPage + 1 : perPage;
    }

    /** Restoring used to wait for the first new message, so a quiet join showed an empty chat. */
    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("HEAD")
    )
    private void blackaddons$restoreOnFirstDraw(CallbackInfo ci) {
        blackaddons$restoreSaved();
    }

    @WrapOperation(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"
        )
    )
    private void blackaddons$animate(
            ChatComponent self,
            ChatComponent.ChatGraphicsAccess access,
            int screenHeight,
            int ticks,
            ChatComponent.DisplayMode mode,
            Operation<Void> original,
            @Local(argsOnly = true) GuiGraphicsExtractor graphics
    ) {
        int scroll = chatScrollbarPos;
        long now = System.nanoTime();
        double seconds = (now - blackaddons$lastFrame) / 1_000_000_000.0;
        blackaddons$lastFrame = now;
        blackaddons$drawnScroll = isChatFocused()
            ? ChatMotions.INSTANCE.scroll(blackaddons$drawnScroll, scroll, seconds)
            : scroll;
        double drawn = blackaddons$drawnScroll;

        int base = (int) Math.floor(drawn);
        float shift = (float) (drawn - base);
        if (scroll == 0 && drawn == 0.0 && blackaddons$newestAt != 0L) {
            shift += ChatMotions.INSTANCE.slide(blackaddons$msSince(blackaddons$newestAt));
        }
        boolean scrolling = drawn != scroll;
        if (!scrolling && shift == 0f) {
            original.call(self, access, screenHeight, ticks, mode);
            return;
        }

        double scale = getScale();
        int lineHeight = getLineHeight();
        if (scrolling) {
            int bottom = (int) Math.floor((screenHeight - 40) / scale);
            int top = bottom - getLinesPerPage() * lineHeight;
            graphics.enableScissor(0, (int) (top * scale), graphics.guiWidth(), (int) Math.ceil(bottom * scale));
            chatScrollbarPos = base;
            blackaddons$extraLine = true;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, (float) (shift * lineHeight * scale));
        try {
            original.call(self, access, screenHeight, ticks, mode);
        } finally {
            graphics.pose().popMatrix();
            if (scrolling) {
                graphics.disableScissor();
                chatScrollbarPos = scroll;
                blackaddons$extraLine = false;
            }
        }
    }

    @WrapOperation(
        method = "lambda$extractRenderState$1",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V")
    )
    private static void blackaddons$highlight(
            ChatComponent.ChatGraphicsAccess graphics,
            int x0, int y0, int x1, int y1, int color,
            Operation<Void> original,
            @Local(argsOnly = true) GuiMessage.Line line,
            @Local(argsOnly = true, ordinal = 1) float alpha
    ) {
        original.call(graphics, x0, y0, x1, y1, color);
        ChatLine chatLine = (ChatLine) (Object) line.parent();
        Color highlight = chatLine.getHighlight();
        if (highlight == null) return;
        int tint = ((int) (highlight.getAlpha() * alpha) << 24) | (highlight.getRGB() & 0xFFFFFF);
        Regex matches = chatLine.getHighlightMatches();
        if (matches == null) {
            graphics.fill(x0, y0, x1, y1, tint);
            return;
        }
        FormattedCharSequence row = line.content();
        StringBuilder text = new StringBuilder();
        row.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        Font font = Minecraft.getInstance().font;
        Matcher matcher = matches.toPattern().matcher(text);
        while (matcher.find()) {
            graphics.fill(blackaddons$widthOf(font, row, matcher.start()), y0, blackaddons$widthOf(font, row, matcher.end()), y1, tint);
        }
    }

    @Unique
    private static int blackaddons$widthOf(Font font, FormattedCharSequence row, int chars) {
        return font.width((FormattedCharSequence) sink -> {
            int[] taken = {0};
            return row.accept((index, style, codePoint) -> {
                taken[0] += Character.charCount(codePoint);
                return taken[0] <= chars && sink.accept(index, style, codePoint);
            });
        });
    }

    @Unique
    private static double blackaddons$msSince(long nanos) {
        return (System.nanoTime() - nanos) / 1_000_000.0;
    }
}

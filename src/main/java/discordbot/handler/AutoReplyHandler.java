package discordbot.handler;

import discordbot.db.model.OReplyPattern;
import discordbot.db.table.TGuild;
import discordbot.db.table.TReplyPattern;
import discordbot.main.DiscordBot;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.PrivateChannel;
import net.dv8tion.jda.entities.TextChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the automatic responses to messages
 */
public class AutoReplyHandler {
	private DiscordBot bot;
	private AutoReply[] replies;
	private volatile Map<String, Long[]> cooldowns;

	public AutoReplyHandler(DiscordBot bot) {
		this.bot = bot;
		cooldowns = new ConcurrentHashMap<>();
		reload();
	}

	public boolean autoReplied(Message message) {
		if (message.getChannel() instanceof PrivateChannel) {
			return false;
		}
		if (!(message.getChannel() instanceof TextChannel)) {
			return false;
		}
		TextChannel channel = (TextChannel) message.getChannel();
		String guildId = channel.getGuild().getId();
		int internalGuildId = TGuild.getCachedId(guildId);
		Long now = System.currentTimeMillis();
		for (int index = 0; index < replies.length; index++) {
			if (replies[index].guildId == 0 || replies[index].guildId == internalGuildId) {
				Long lastUse = getCooldown(guildId, index);
				if (lastUse + replies[index].cooldown < now) {
					Matcher matcher = replies[index].pattern.matcher(message.getContent());
					if (matcher.matches()) {
						saveCooldown(guildId, index, now);
						bot.out.sendAsyncMessage(channel, message.getAuthor().getAsMention() + ", " + replies[index].reply, null);
						return true;
					}
				}
			}
		}

		return false;
	}

	private long getCooldown(String guildId, int index) {
		if (!cooldowns.containsKey(guildId)) {
			cooldowns.put(guildId, new Long[replies.length]);
		}
		if (cooldowns.get(guildId)[index] == null) {
			return 0;
		}
		return cooldowns.get(guildId)[index];
	}

	private void saveCooldown(String guildId, int index, long value) {
		if (!cooldowns.containsKey(guildId)) {
			cooldowns.put(guildId, new Long[replies.length]);
		}
		cooldowns.get(guildId)[index] = value;
	}

	public void reload() {
		List<OReplyPattern> all = TReplyPattern.getAll();
		replies = new AutoReply[all.size()];
		int index = 0;
		for (OReplyPattern reply : all) {
			AutoReply ar = new AutoReply();
			ar.pattern = Pattern.compile(reply.pattern);
			ar.tag = reply.tag;
			ar.cooldown = reply.cooldown;
			ar.reply = reply.reply;
			ar.guildId = reply.guildId;
			replies[index++] = ar;
		}
	}

	private class AutoReply {

		public Pattern pattern;
		public String tag;
		public long cooldown;
		public String reply;
		public int guildId;
	}
}

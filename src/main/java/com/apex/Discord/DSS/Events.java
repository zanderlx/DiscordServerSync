package com.apex.Discord.DSS;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Events extends ListenerAdapter
{
	public static final Events INSTANCE = new Events();
	private final Map<String, TriConsumer<TextChannel, User, String[]>> commandMap = new HashMap<>();

	private Events()
	{
		commandMap.put("ping", (channel, user, args) -> channel.sendMessage(String.format("%s Pong!", user.getName())).queue());
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		User author = event.getAuthor();
		Message message = event.getMessage();
		TextChannel channel = event.getChannel();

		if(processMessageAsCommand(channel, message, author))
			return;
	}

	private boolean processMessageAsCommand(TextChannel channel, Message message, User author)
	{
		String msg = message.getContentRaw();

		if(message.isWebhookMessage())
			return false;

		if(msg.startsWith("!"))
		{
			// 'fake' process the message
			// bots cant execute commands
			if(author.isBot())
				return true;

			// remove '!' and split by whitespaces
			String[] tokens = msg.substring(1).split("\\s+");
			String commandName = tokens[0].toLowerCase();
			String[] commandArgs = new String[tokens.length - 1];

			// i = commandArg
			// i + 1 = commandArg in tokensArray
			// dupe tokens but without command name
			Arrays.setAll(commandArgs, i -> tokens[i + 1]);

			TriConsumer<TextChannel, User, String[]> command = commandMap.get(commandName);

			if(command != null)
				command.accept(channel, author, commandArgs);
			return true;
		}
		return false;
	}
}

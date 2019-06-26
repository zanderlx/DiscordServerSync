package com.apex.Discord.DSS;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Events extends ListenerAdapter
{
	public static final Events INSTANCE = new Events();
	private final Map<String, TriConsumer<TextChannel, User, String[]>> commandMap = new HashMap<>();

	private Events()
	{
		// commandMap.put("ping", (channel, user, args) -> channel.sendMessage(String.format("%s Pong!", user.getName())).queue());

		commandMap.put("sync", this::syncCommand);
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

	private void syncCommand(TextChannel channel, User sender, String[] args)
	{
		final Consumer<Message> deleteAfter5 = m -> m.delete().queueAfter(5, TimeUnit.SECONDS);

		if(args.length == 0)
		{
			channel.sendMessage("Invalid number of arguments passed").queue(deleteAfter5);
			return;
		}

		String sub = args[0];

		// marks guild the command was run from
		// to be synced from the parent guild
		if(sub.equalsIgnoreCase("add"))
		{
			Guild guild = channel.getGuild();
			Guild parent = Main.getParentGuild();

			if(guild.getIdLong() == parent.getIdLong())
				channel.sendMessage(String.format("%s can not sync with itself", guild.getName())).queue(deleteAfter5);
			else if(Main.isChildGuild(guild))
				channel.sendMessage(String.format("%s is already syncing with %s", guild.getName(), parent.getName())).queue(deleteAfter5);
			else
			{
				Main.addChildGuild(guild);
				channel.sendMessage(String.format("%s will now be synced with %s", guild.getName(), parent.getName())).queue(deleteAfter5);
			}
		}
	}
}

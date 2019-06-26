package com.apex.Discord.DSS;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@UtilityClass
public class Main extends ListenerAdapter
{
	private JDA jda;
	private final Map<String, String> args = new HashMap<>();

	public static void main(String[] pArgs)
	{
		readArgs(pArgs);

		if(!startUp())
			return;

		loop();
		shutdown();
	}

	private void readArgs(String[] pArgs)
	{
		// --<key> <value>, program args
		// --oauth {} - bot oauth token
		for(int i = 0; i < pArgs.length; i++)
		{
			String key = pArgs[i];

			if(i + 1 > pArgs.length - 1)
				break;

			String value = pArgs[i + 1];

			if(!key.startsWith("--"))
				continue;

			args.put(key.substring(2).toLowerCase(), value);
		}
	}

	private boolean startUp()
	{
		log.info("Connecting to Discord...");

		try
		{
			jda = new JDABuilder(args.get("oauth")).build();
		}
		catch(LoginException e)
		{
			jda = null;
			log.error("Failed to connect to Discord servers.", e);
		}

		if(jda == null)
		{
			shutdown();
			return false;
		}

		log.info("Successfully connected to Discord servers.");
		jda.addEventListener(Events.INSTANCE);
		return true;
	}

	private void loop()
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		while(true)
		{
			String input;

			try
			{
				input = reader.readLine();
			}
			catch(IOException e)
			{
				input = null;
				log.error("Failed to read user input", e);

				try
				{
					Thread.sleep(1);
				}
				catch(InterruptedException ignored) { }
			}

			if(input == null || input.isEmpty())
				continue;
			if(input.equalsIgnoreCase("shutdown"))
				break;
		}
	}

	private void shutdown()
	{
		log.info("Shutting down bot...");

		if(jda != null)
		{
			log.info("Disconnecting from Discord servers...");
			jda.shutdown();
		}
	}
}

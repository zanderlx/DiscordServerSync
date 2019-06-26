package com.apex.Discord.DSS;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@UtilityClass
public class Main extends ListenerAdapter
{
	@Nullable private JDA jda;
	private final Gson GSON = new GsonBuilder().create();

	// suppress nullable warnings
	// this field is never null
	// only time it is, bot *should* die out and stops running
	@SuppressWarnings("NullableProblems") private JsonObject config;

	public static void main(String[] pArgs)
	{
		// bot.json must be in same dir
		// as jar is running from
		if(!readConfig())
			return;
		if(!startUp())
			return;

		loop();
		shutdown();
	}

	private boolean readConfig()
	{
		try
		{
			config = GSON.fromJson(new FileReader(new File("./bot.json")), JsonObject.class);
		}
		catch(FileNotFoundException e)
		{
			log.error("Failed to read bot json config", e);
			return false;
		}
		return true;
	}

	private boolean startUp()
	{
		log.info("Connecting to Discord...");

		try
		{
			jda = new JDABuilder(config.get("oauth").getAsString()).build();
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

package com.apex.Discord.DSS;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@UtilityClass
public class Main extends ListenerAdapter
{
	private final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// suppress nullable warnings
	// these field should never null
	// only time they can be null, the but *should* die out and shutdown
	@SuppressWarnings("NullableProblems") private JDA jda;
	@SuppressWarnings("NullableProblems") private JsonObject config;

	private List<Long> children = new ArrayList<>();

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

		// copy children ids from json file to array
		config.get("children").getAsJsonArray().forEach(e -> children.add(e.getAsLong()));

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
		// writeConfig();
		log.info("Disconnecting from Discord servers...");
		jda.shutdown();
	}

	public JDA getJda()
	{
		return jda;
	}

	public Guild getParentGuild()
	{
		return jda.getGuildById(config.get("guild_base").getAsLong());
	}

	public boolean isChildGuild(Guild guild)
	{
		return children.contains(guild.getIdLong());
	}

	public void addChildGuild(Guild guild)
	{
		children.add(guild.getIdLong());
		writeConfig();
	}

	private void writeConfig()
	{
		// update children
		JsonArray newChildren = new JsonArray();
		children.forEach(newChildren::add);
		config.add("children", newChildren);

		// write to file
		try
		{
			// this replaced with blank file
			// GSON.toJson(config, new FileWriter(new File("./bot.json")));

			// wrote out the json file fine
			Files.write(Paths.get("./bot.json"), Collections.singleton(GSON.toJson(config)));
		}
		catch(IOException e)
		{
			log.error("Failed to write bot.json");
		}
	}
}

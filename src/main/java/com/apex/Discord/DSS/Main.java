package com.apex.Discord.DSS;

import com.google.gson.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.RestAction;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
	private Map<Long, Map<Long, Long>> roleIdMap = new HashMap<>();

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

		// copy role ids from config
		JsonObject roles = config.get("roles").getAsJsonObject();

		roles.keySet().forEach(key -> {
			Map<Long, Long> map = new HashMap<>();
			JsonObject child = roles.getAsJsonObject(key);
			child.keySet().forEach(sKey -> map.put(Long.valueOf(sKey), child.get(sKey).getAsLong()));
			roleIdMap.put(Long.valueOf(key), map);
		});

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

		// Java 1.9
		CompletableFuture.delayedExecutor(config.get("sync_cooldown").getAsInt(), TimeUnit.SECONDS).execute(Main::syncChildren);

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

			if(input.equalsIgnoreCase("sync"))
				syncChildren();
			else if(input.equalsIgnoreCase("shutdown"))
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

	private void syncChildren()
	{
		SelfUser selfUser = jda.getSelfUser();
		Guild parent = getParentGuild();
		Member bot = parent.getMember(selfUser);

		if(bot.hasPermission(Permission.MANAGE_ROLES))
		{
			List<RestAction<Void>> queue = new LinkedList<>();

			// loop over all roles
			parent.getRoles().forEach(role -> {
				// get users with role
				parent.getMembersWithRoles(role).forEach(member -> {
					// get user
					User user = member.getUser();

					// dont modify bots
					if(user.isBot())
						return;

					// loop over all child guilds
					children.forEach(id -> {
						Guild child = jda.getGuildById(id);

						// bot does not have permission in child guild
						if(!child.getMember(selfUser).hasPermission(Permission.MANAGE_ROLES, Permission.ADMINISTRATOR))
						{
							log.warn("Bot does have MANAGER_ROLES permission in guild {}", child.getName());
							return;
						}

						Member cMember = child.getMember(user);
						Role cRole = getOrCreateRole(child, role);

						// user is in parent and child guilds
						if(cMember != null)
						{
							// user does not have role in child guild
							// add role to user
							if(!cMember.getRoles().contains(cRole))
							{
								log.info("Role synced for user, {}, {}, {}", user.getName(), role.getName(), child.getName());
								AuditableRestAction<Void> a = child.getController().addRolesToMember(cMember, cRole);
								queue.add(a); // delay adding roles until after all guilds, members and roles have been processed
							}
						}
						else
							log.warn("User is not in parent & child guilds, {}, {}/{}", user.getName(), parent.getName(), child.getName());
					});
				});
			});

			if(!queue.isEmpty())
				queue.forEach(RestAction::complete);
		}
		else
			log.warn("Bot does have MANAGER_ROLES permission in guild {}", parent.getName());

		// Java 1.9 - mark this to be called again later
		CompletableFuture.delayedExecutor(config.get("sync_cooldown").getAsInt(), TimeUnit.SECONDS).execute(Main::syncChildren);
	}

	private Role getOrCreateRole(Guild guild, Role role)
	{
		// this method does not check if bot has permissions
		// that should be checked and handled prior to calling this method
		// MANAGE_ROLES, ADMINISTRATOR

		// guild = child guild
		// role = role from parent guild
		// role will not exist on child guild
		// this method should create role with same data
		// on the child guild and store ids so that
		// the same role can be accessed again later

		// pass any role from parent guild should return
		// copy of the role created in the child guild

		// Json
		/*
		{
			"roles": {
				"<role_id_from_parent>": {
					"<child_guild_id>": "<child_guild_role_id>"
				}
			}
		}
		*/

		Guild parent = getParentGuild();
		long roleIdFromParent = role.getIdLong();
		long childGuildId = guild.getIdLong();

		// just return the role if we are in the parent guild
		// no need to check or create role
		// it should already exist
		if(parent.getIdLong() == childGuildId)
			return role;

		// get role ids for this role
		Map<Long, Long> map = roleIdMap.getOrDefault(roleIdFromParent, new HashMap<>());

		// has this role been created
		if(map.containsKey(childGuildId))
		{
			long roleIdFromChildGuild = map.get(childGuildId);
			Role r = guild.getRoleById(roleIdFromChildGuild);

			// if role was deleted on guild
			// create create the role
			if(r != null)
				return r;
		}

		// create a duplicate role in the child guild
		Role childRole = role.createCopy(guild).complete();
		map.put(childGuildId, childRole.getIdLong());
		roleIdMap.put(roleIdFromParent, map);
		log.info("Role({}) duplicated from {} into {}", role.getName(), parent.getName(), guild.getName());
		writeConfig();
		return childRole;
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
		if(!isChildGuild(guild))
		{
			children.add(guild.getIdLong());
			writeConfig();
		}
	}

	public void removeChildGuild(Guild guild)
	{
		if(isChildGuild(guild))
		{
			children.remove(guild.getIdLong());
			writeConfig();
		}
	}

	private void writeConfig()
	{
		// update children
		JsonArray newChildren = new JsonArray();
		children.forEach(newChildren::add);
		config.add("children", newChildren);

		// update roles
		JsonObject newRoles = new JsonObject();

		roleIdMap.keySet().forEach(key -> {
			JsonObject obj = new JsonObject();

			roleIdMap.get(key).forEach((sKey, value) -> {
				obj.addProperty(sKey.toString(), value);
			});

			newRoles.add(key.toString(), obj);
		});

		config.add("roles", newRoles);

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

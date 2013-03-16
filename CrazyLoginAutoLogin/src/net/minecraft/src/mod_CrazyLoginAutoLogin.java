package net.minecraft.src;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

public class mod_CrazyLoginAutoLogin extends BaseMod
{

	private final static Pattern PATTERN_SPACE = Pattern.compile(" ");
	private final static Pattern PATTERN_EQUALSIGN = Pattern.compile("=");
	private final static char[] chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.,_".toCharArray();
	private final Random random = new Random();
	private Minecraft minecraft;
	private String server = null;
	private String sAuth = null;
	private String password = null;

	@Override
	public String getName()
	{
		return "CrazyLogin";
	}

	@Override
	public String getVersion()
	{
		return "0.1";
	}

	@Override
	public void load()
	{
		this.minecraft = ModLoader.getMinecraftInstance();
		getDataFolder().mkdirs();
		// Listen to ticks
		ModLoader.setInGameHook(this, true, true);
		// Register channel
		ModLoader.registerPacketChannel(this, "CrazyLogin");
		// log("StartUp");
	}

	@Override
	public void clientConnect(final NetClientHandler handler)
	{
		final INetworkManager networkManager = handler.getNetManager();
		if (networkManager == null)
			server = null;
		else
			server = networkManager.getSocketAddress().toString();
		log("Connect", server);
		for (final char c : ChatAllowedCharacters.allowedCharactersArray)
			server = server.replace(c, '_');
		sAuth = null;
		password = null;
		sendPacket("Q_Ping");
		sendPacket("Q_sAuth");
	}

	@Override
	public void clientDisconnect(final NetClientHandler clientHandler)
	{
		// log("Disconnect");
		server = null;
		sAuth = null;
		password = null;
	}

	@Override
	public void clientCustomPayload(final NetClientHandler nch, final Packet250CustomPayload packet)
	{
		if (packet.channel.equals("CrazyLogin"))
		{
			final String data = new String(packet.data, Charset.forName("UTF-8"));
			// log("Packet", data);
			final String[] split = PATTERN_SPACE.split(data, 2);
			final String header = split[0];
			final String args;
			if (split.length == 1)
				args = "";
			else
				args = split[1];
			if (header.startsWith("Q_"))
				if (header.equalsIgnoreCase("Q_Ping"))
					sendPacket("A_Ping CrazyLoginAutoLogin");
				else if (header.equalsIgnoreCase("Q_Version"))
					sendPacket("A_Version " + getVersion());
				else
					pluginMessageQuerryRecieved(header.substring(2), args);
			else if (header.startsWith("A_"))
				pluginMessageAnswerRecieved(header.substring(2), args);
		}
	}

	private void pluginMessageQuerryRecieved(final String header, final String args)
	{
		if (sAuth == null)
			sendPacket("sAuth");
		else if (header.equals("StorePW"))
		{
			password = args;
			saveServer();
			sendPacket("A_StorePW true");
		}
	}

	private void pluginMessageAnswerRecieved(final String header, final String args)
	{
		if (header.equals("sAuth"))
		{
			sAuth = args;
			loadServer();
			sendPacket("Q_State");
		}
		else if (sAuth == null)
			sendPacket("sAuth");
		else if (header.equals("State"))
			if (args.startsWith("0"))
			{
				password = genPassword();
				saveServer();
				sendPacket("Q_ChgPW " + password);
			}
			else if (args.startsWith("1"))
				if (password != null)
					if (args.endsWith("0"))
						sendPacket("Q_Login " + password);
	}

	private String genPassword()
	{
		final StringBuilder builder = new StringBuilder(10);
		final int length = chars.length;
		for (int i = 0; i < 10; i++)
			builder.append(chars[random.nextInt(length)]);
		return builder.toString();
	}

	private void loadServer()
	{
		if (server == null || sAuth == null)
		{
			password = null;
			return;
		}
		BufferedReader reader = null;
		try
		{
			final File file = new File(getDataFolder(), server + ".dat");
			if (!file.exists())
				return;
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String zeile = null;
			while ((zeile = reader.readLine()) != null)
			{
				final String[] split = PATTERN_EQUALSIGN.split(zeile, 2);
				if (split.length != 2)
					continue;
				if (split[0].equals(sAuth))
					password = split[1];
			}
		}
		catch (final IOException e)
		{}
		finally
		{
			if (reader != null)
				try
				{
					reader.close();
				}
				catch (final IOException e)
				{}
		}
	}

	private void saveServer()
	{
		if (server == null || sAuth == null || password == null)
			return;
		BufferedWriter writer = null;
		try
		{
			final File file = new File(getDataFolder(), server + ".dat");
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
			writer.write(sAuth + "=" + password + '\n');
		}
		catch (final IOException e)
		{}
		finally
		{
			if (writer != null)
				try
				{
					writer.close();
				}
				catch (final IOException e)
				{}
		}
	}

	public void log(final String subject, final Object... messages)
	{
		try
		{
			final FileWriter writer = new FileWriter(new File(getDataFolder(), "plugin.log"), true);
			writer.write(subject + ": ");
			if (messages.length == 0)
				writer.write('\n');
			else
				for (final Object message : messages)
					writer.write(message + "\n");
			writer.close();
		}
		catch (final IOException e)
		{}
	}

	public File getDataFolder()
	{
		return new File(Minecraft.getMinecraftDir(), "mods" + File.separator + "CrazyLogin");
	}

	public boolean isMultiplayer()
	{
		return !this.minecraft.isSingleplayer();
	}

	public void sendPacket(final String message)
	{
		final byte[] bytes = message.getBytes();
		sendPacket(bytes.length, bytes);
	}

	public void sendPacket(final int size, final byte[] bytes)
	{
		final Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CrazyLogin";
		packet.length = size;
		packet.data = bytes;
		ModLoader.clientSendPacket(packet);
	}
}

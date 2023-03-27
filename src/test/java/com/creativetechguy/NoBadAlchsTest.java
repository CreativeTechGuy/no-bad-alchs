package com.creativetechguy;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NoBadAlchsTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NoBadAlchsPlugin.class);
		RuneLite.main(args);
	}
}
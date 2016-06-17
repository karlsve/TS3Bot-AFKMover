package de.karlsve.ts3.plugins;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import de.karlsve.ts3.Log;
import de.karlsve.ts3.ServerBot;
import de.karlsve.ts3.ServerBot.TickListener;
import de.karlsve.ts3.command.PrivateCommand;
import de.karlsve.ts3.events.MessageEvent;
import de.karlsve.ts3.model.Client;
import de.karlsve.ts3.plugins.Plugin;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

public class AFKMover implements Plugin {
	
	class AFKTickListener implements TickListener {
		
		private AFKMover mover;

		public AFKTickListener(AFKMover mover) {
			this.mover = mover;
		}

		@Override
		public void onTick(ServerBot handle) {
			synchronized (AFKMover.block) {
				int afk_cid = Integer.valueOf(handle.getSettings().get("afk_cid"));
				try {
					for (Client client : Client.getClientList(handle, "away")) {
						int clid = Integer.valueOf(client.get("clid"));
						int user_cid = Integer.valueOf(client.get("cid"));
						if (user_cid != afk_cid && !this.mover.afk.containsKey(clid) && !this.mover.ignore.contains(clid)) {
							if (AFKMover.isClientAFK(client)) {
								Log.d(String.format("Moving to afk_channel: {%d:{%d:%d}}", clid, user_cid, afk_cid));
								this.mover.afk.put(clid, user_cid);
								handle.getQuery().moveClient(clid, afk_cid, handle.getSettings().get("afk_passwd"));
								handle.getQuery().sendTextMessage(clid, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT,
										"You were moved to the AFK Channel, because you were inactive for 10 minutes.\nWhen you come back from inactivity I will move you back.\nTo disable this functionality please answer to this with \"afk off\".");
							}
						}
					}
				} catch (TS3ServerQueryException e) {
					Log.e(e);
				}
				Iterator<Map.Entry<Integer, Integer>> it = this.mover.afk.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<Integer, Integer> entry = it.next();
					try {
						Client client = new Client(handle, entry.getKey());
						int clid = Integer.valueOf(client.get("clid"));
						int user_cid = Integer.valueOf(client.get("cid"));
						if (user_cid == afk_cid) {
							if (!AFKMover.isClientAFK(client)) {
								Log.d(String.format("Moving back: {%d:{%d:%d}}", clid, user_cid, entry.getValue()));
								it.remove();
								handle.getQuery().moveClient(clid, entry.getValue(), "");
							}
						} else {
							Log.d(String.format("Removing from afk_list: {%d}", clid));
							it.remove();
						}
					} catch (TS3ServerQueryException e) {
						Log.d(String.format("Removing from afk_list: {%d}", entry.getKey()));
						it.remove();
					}
				}
			}
		}
		
	}

	class DisableCommand extends PrivateCommand {

		private AFKMover mover = null;

		public DisableCommand(ServerBot handle, AFKMover mover) {
			super(handle, "^afk off$");
			this.mover = mover;
		}

		@Override
		protected void onTrigger(MessageEvent event) {
			try {
				this.mover.ignore(event.getInvokerId());
				this.getHandle().getQuery().sendTextMessage(event.getInvokerId(),
						JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT,
						"You have disabled the AFKMove functionality.\nTo enable this functionality please answer to this with \"afk on\".");
			} catch (TS3ServerQueryException e) {
				Log.e(e);
			}
		}

	}

	class EnableCommand extends PrivateCommand {

		private AFKMover mover = null;

		public EnableCommand(ServerBot handle, AFKMover mover) {
			super(handle, "^afk on$");
			this.mover = mover;
		}

		@Override
		protected void onTrigger(MessageEvent event) {
			try {
				this.mover.unignore(event.getInvokerId());
				this.getHandle().getQuery().sendTextMessage(event.getInvokerId(),
						JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT,
						"You have enabled the AFKMove functionality.\nTo disable this functionality please answer to this with \"afk off\".");
			} catch (Exception e) {
				Log.e(e);
			}
		}

	}

	private Map<Integer, Integer> afk = new HashMap<>();
	private Vector<Integer> ignore = new Vector<Integer>();
	private static Object block = new Object();
	private AFKTickListener listener;
	
	public AFKMover() {
		this.listener = new AFKTickListener(this);
	}

	private static boolean isClientAFK(Client client) {
		return client.get("client_input_muted").equals("1") && client.get("client_output_muted").equals("1") && Integer.valueOf(client.get("client_idle_time")) > (10 * (60 * 1000));
	}

	@Override
	public void onLoad(ServerBot handle) {
		if(handle.getSettings().containsKey("afk_cid") && handle.getSettings().containsKey("afk_passwd")) {
			handle.addListener(this.listener);
			handle.getCommandManager().registerCommand(new EnableCommand(handle, this));
			handle.getCommandManager().registerCommand(new DisableCommand(handle, this));
		}
	}

	@Override
	public void onUnload(ServerBot handle) {
		handle.removeListener(this.listener);
	}

	public void ignore(int clid) {
		this.ignore.add(clid);
	}

	public void unignore(int clid) {
		this.ignore.removeElement(clid);
	}

}

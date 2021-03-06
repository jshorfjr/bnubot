/**
 * This file is distributed under the GPL
 * $Id$
 */

package net.bnubot.util;

import org.apache.cayenne.ObjectContext;

import net.bnubot.core.Connection;
import net.bnubot.db.Account;
import net.bnubot.db.Rank;
import net.bnubot.db.conf.DatabaseContext;
import net.bnubot.logging.Out;
import net.bnubot.settings.GlobalSettings;
import net.bnubot.vercheck.CurrentVersion;
import net.bnubot.vercheck.ReleaseType;

/**
 * A class responsible for formatting Battle.net usernames.
 * Now it includes support for the database, which will make toString() quite pretty.
 * A BNetUser has a Connection, and can be whispered by .sendChat()
 * @author scotta
 */
public class BNetUser {
	protected final Connection con;
	private String shortLogonName;	// #=yes, realm=only if different from "myRealm"
	private String fullLogonName;	// #=yes, realm=yes
	private final String fullAccountName;	// #=no, realm=yes
	private String realm = null;
	private int flags = 0;
	private Integer ping = null;
	private StatString statString = null;

	private String lastToString = null;
	private long lastToStringTime = 0;

	/**
	 * Constructor for a BNetUser
	 * @param user User[#N]@Realm
	 */
	public BNetUser(String user) {
		this(null, user, (String)null);
	}

	/**
	 * Constructor for a BNetUser
	 * @param con	The connection the user is on
	 * @param user	User[#N][@Realm]
	 * @param perspectiveOf	BNetUser to infer realm from
	 */
	public BNetUser(Connection con, String user, BNetUser perspectiveOf) {
		this(con, user, perspectiveOf.realm);
	}

	/**
	 * Constructor for a BNetUser
	 * @param con	The connection the user is on
	 * @param user	"User[#N][@Realm]"
	 * @param myRealm	"[User[#N]@]Realm" to infer realm from
	 */
	public BNetUser(Connection con, String user, String myRealm) {
		this.con = con;
		String uAccount;
		int uNumber = 0;

		//System.out.println(user);

		/*
		int i = user.indexOf('@');
		if(i != -1) {
			String num = user.substring(i + 1);
			int j = num.indexOf('#');
			if(j != -1) {
				num = num.substring(0, j);
				this.realm = user.substring(i + j + 2);
				user = user.substring(0, i) + '@' + this.realm;
				System.out.println(user);
			} else {
				user = user.substring(0, i);
			}

			uNumber = Integer.parseInt(num);
		}
		*/
		String up[] = user.split("@", 2);
		uAccount = up[0];
		if(up.length == 2)
			this.realm = up[1];
		else {
			if((myRealm == null)
			|| (myRealm.indexOf('@') != -1))
				throw new IllegalStateException();
			this.realm = myRealm;
		}

		// ...
		shortLogonName = uAccount;
		if(uNumber != 0)
			shortLogonName += "#" + uNumber;
		if(!this.realm.equals(myRealm))
			shortLogonName += "@" + this.realm;

		// ...
		fullLogonName = uAccount;
		if(uNumber != 0)
			fullLogonName += "#" + uNumber;
		fullLogonName += "@" + this.realm;

		// ...
		fullAccountName = uAccount + "@" + this.realm;
	}

	/**
	 * Gets the shortest possible logon name
	 * @return User[#N][@Realm]
	 */
	public String getShortLogonName() {
		return shortLogonName;
	}

	/**
	 * Gets the shortest possible logon name, as another user sees it
	 * @param perspective the value returned will be from this user's perspective
	 * @return User[#N][@Realm]
	 */
	public String getShortLogonName(BNetUser perspective) {
		if((perspective != null)
		&& this.realm.equals(perspective.realm)) {
			//return shortLogonName.split("@")[0];
			int x = shortLogonName.lastIndexOf('@');
			if(x == -1)
				return shortLogonName;
			return shortLogonName.substring(0, x);
		}
		return fullLogonName;
	}

	/**
	 * Gets the full logon name
	 * @return User[#N]@Realm
	 */
	public String getFullLogonName() {
		return fullLogonName;
	}

	/**
	 * Gets the full account name
	 * @return "User@Realm"
	 */
	public String getFullAccountName() {
		return fullAccountName;
	}

	/**
	 * Resets the pretty name back to null, so it will be re-evaluated next time toString() is called
	 */
	public void resetPrettyName() {
		lastToString = null;
		lastToStringTime = 0;
	}

	private String getShortPrettyName() {
		ObjectContext context = DatabaseContext.getContext();
		if(context == null)
			return shortLogonName;

		String shortPrettyName = shortLogonName;
		try {
			Account account = Account.get(this);
			if(account != null) {
				String name = account.getName();
				if(name != null)
					shortPrettyName = name;

				Rank rank = account.getRank();
				if(rank != null) {
					String prefix = rank.getShortPrefix();
					if(prefix == null)
						prefix = rank.getPrefix();
					if(prefix != null)
						shortPrettyName = prefix + " " + shortPrettyName;
				}
			}
		} catch(Exception e) {
			Out.exception(e);
		}

		return shortPrettyName;
	}

	/**
	 * Equivalent to getShortLogonName if there is no database or if the user isn't in it;
	 * @return User[#N][@Realm] or [Prefix ][Account (]FullLogonName[)]
	 */
	private String getPrettyName() {
		ObjectContext context = DatabaseContext.getContext();
		if(context == null)
			return shortLogonName;

		String prettyName = shortLogonName;
		try {
			Account account = Account.get(this);
			if(account != null) {
				String name = account.getName();
				if(name != null)
					prettyName = name + " (" + prettyName + ")";

				Rank rank = account.getRank();
				if(rank != null) {
					String prefix = rank.getPrefix();
					if(prefix != null)
						prettyName = prefix + " " + prettyName;
				}
			}
		} catch(Exception e) {
			Out.exception(e);
		}

		return prettyName;
	}

	/**
	 * Equivalent to getShortLogonName if there is no database or if the user isn't in it;
	 * @return User[#N][@Realm] or &lt;Account> [(FullLogonName)]
	 */
	private String getAccountAndLogin() {
		ObjectContext context = DatabaseContext.getContext();
		if(context == null)
			return shortLogonName;

		String prettyName = shortLogonName;
		try {
			Account account = Account.get(this);
			if(account != null) {
				String name = account.getName();
				if(name != null)
					prettyName = name + " (" + prettyName + ")";
			}
		} catch(Exception e) {
			Out.exception(e);
		}

		return prettyName;
	}

	/**
	 * Equivalent to getShortLogonName if there is no database or if the user isn't in it;
	 * @return User[#N][@Realm] or &lt;Account>
	 */
	private String getAccountOrLogin() {
		ObjectContext context = DatabaseContext.getContext();
		if(context == null)
			return shortLogonName;

		String prettyName = shortLogonName;
		try {
			Account account = Account.get(this);
			if(account != null) {
				String name = account.getName();
				if(name != null)
					prettyName = name;
			}
		} catch(Exception e) {
			Out.exception(e);
		}

		return prettyName;
	}

	/**
	 * Returns user-desirable display string
	 */
	@Override
	public String toString() {
		// Check if we should re-generate the string; cache it for five seconds
		if((lastToString == null)
		|| (System.currentTimeMillis() - lastToStringTime > 5000)) {
			lastToStringTime = System.currentTimeMillis();
			lastToString = toString(GlobalSettings.bnUserToString);
		}

		return lastToString;
	}

	public String toString(int type) {
		switch(type) {
		case 0: return getFullLogonName();		// BNLogin@Gateway
		case 1: return getShortLogonName();		// BNLogin
		case 2: return getShortPrettyName();	// ShortPrefix Account
		case 3: return getPrettyName();			// Prefix Account (BNLogin)
		case 4: return getAccountOrLogin();		// Account
		case 5: return getAccountAndLogin();	// Account (BNLogin)
		}
		throw new UnloggedException("Unknown BNetUser.toString(int) type " + GlobalSettings.bnUserToString);
	}

	public String toStringEx() {
		String out = toString();
		if(ping != null)
			out += " [" + ping + "ms]";
		if(flags != 0) {
			out += " (";
			if((flags & 0x01) != 0)
				out += "Blizzard Representative, ";
			if((flags & 0x08) != 0)
				out += "Battle.net Representative, ";
			if((flags & 0x02) != 0)
				out += "Channel Operator, ";
			if((flags & 0x04) != 0)
				out += "Speaker, ";
			if((flags & 0x10) != 0)
				out += "No UDP Support, ";
			if((flags & 0x40) != 0)
				out += "Battle.net Guest, ";
			if((flags & 0x20) != 0)
				out += "Squelched, ";
			if((flags & 0x100000) != 0)
				out += "GF Official, ";
			if((flags & 0x200000) != 0)
				out += "GF Player, ";
			int flags2 = flags & ~0x30007F;
			if(flags2 != 0)
				out += "0x" + Integer.toHexString(flags2) + ", ";
			out = out.substring(0, out.length() - 2);
			out += ")";
		}
		return out;
	}

	@Override
	public boolean equals(Object o) {
		if(o == null)
			return false;
		if(o == this)
			return true;

		if(o instanceof String) {
			String s = (String)o;
			if(s.equalsIgnoreCase(shortLogonName))
				return true;
			if(s.equalsIgnoreCase(fullLogonName))
				return true;
		} else if(o instanceof BNetUser) {
			BNetUser u = (BNetUser)o;
			if(u.fullLogonName.equalsIgnoreCase(fullLogonName))
				return true;
		} else {
			throw new IllegalArgumentException("Unknown type " + o.getClass().getName());
		}

		return false;
	}

	@Override
	public int hashCode() {
		return fullLogonName.hashCode();
	}

	public int getFlags() {
		return flags;
	}

	public void setFlags(int flags) {
		this.flags = flags;

		ObjectContext context = DatabaseContext.getContext();
		if(context != null)
			try {
				Account account = Account.get(this);
				if(account != null)
					this.flags |= account.getFlagSpoof();
			} catch(Exception e) {
				Out.exception(e);
			}
	}

	public Integer getPing() {
		return ping;
	}

	public void setPing(int ping) {
		this.ping = Integer.valueOf(ping);
	}

	public StatString getStatString() {
		return statString;
	}

	public void setStatString(StatString statString) {
		this.statString = statString;
	}

	/**
	 * Send chat to a user in command response style - either whispered, or formatted with the user's name
	 * @param text The message to send
	 * @param whisperBack Whether to whisper the message
	 * @throws IllegalStateException <code>if(con == null)</code>
	 */
	public void sendChat(String text, boolean whisperBack) {
		int priority = 0;

		Account account = Account.get(this);
		if(account != null)
			priority = account.getAccess();

		sendChat(text, whisperBack, priority);
	}

	/**
	 * Send chat to a user in command response style - either whispered, or formatted with the user's name
	 * @param text The message to send
	 * @param whisperBack Whether to whisper the message
	 * @param priority The priority to use for the ChatQueue
	 * @throws IllegalStateException <code>if(con == null)</code>
	 */
	public void sendChat(String text, boolean whisperBack, int priority) {
		if(text == null)
			return;
		if(con == null)
			throw new IllegalStateException("Can not send chat; connection is null");

		text = con.cleanText(text, true);

		boolean isMyUser = false;
		BNetUser myUser = con.getMyUser();
		if(myUser != null)
			isMyUser = myUser.equals(this);

		if(whisperBack && isMyUser) {
			con.dispatchRecieveInfo(text);
		} else {
			StringBuilder prefix = new StringBuilder();
			if(whisperBack || isMyUser) {
				if(whisperBack)
					prefix.append(getWhisperCommand());

				prefix.append("[BNU");
				ReleaseType rt = CurrentVersion.version().getReleaseType();
				if(rt.isNightly())
					prefix.append(" Nightly");
				else if(rt.isAlpha())
					prefix.append(" Alpha");
				else if(rt.isBeta())
					prefix.append(" Beta");
				else if(rt.isReleaseCandidate())
					prefix.append(" RC");
				prefix.append("] ");
			} else {
				prefix.append(this.toString(GlobalSettings.bnUserToStringCommandResponse));
				prefix.append(": ");
			}

			con.sendChat(prefix.toString(), text, false, true, priority);
		}
	}

	public String getRealm() {
		return realm;
	}

	public String getWhisperCommand() {
		return "/w " + fullLogonName + " ";
	}

	public Connection getConnection() {
		// TODO Auto-generated method stub
		return con;
	}
}

using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using UnityEngine;

public class Session_ME : ISession
{
	public class Sender
	{
		public List<Message> sendingMessage;

		public Sender()
		{
			sendingMessage = new List<Message>();
		}

		public void AddMessage(Message message)
		{
			sendingMessage.Add(message);
		}

		public void run()
		{
			while (connected)
			{
				try
				{
					if (getKeyComplete)
					{
						while (sendingMessage.Count > 0)
						{
							doSendMessage(sendingMessage[0]);
							sendingMessage.RemoveAt(0);
						}
					}
					try
					{
						Thread.Sleep(5);
					}
					catch (Exception e)
					{
						Out.printError(e);
					}
				}
				catch (Exception ex)
				{
					mSystem.outz("error send message! e: " + ex);
				}
			}
		}
	}

	private class MessageCollector
	{
		public void run()
		{
			try
			{
				while (connected)
				{
					Message message = readMessage();
					if (message == null)
					{
						break;
					}
					try
					{
						if (message.command == -27)
						{
							getKey(message);
						}
						else
						{
							onRecieveMsg(message);
						}
					}
					catch (Exception e)
					{
						Out.printError(e);
					}
					try
					{
						Thread.Sleep(5);
					}
					catch (Exception e2)
					{
						Out.printError(e2);
					}
				}
			}
			catch (Exception ex)
			{
				Debug.Log("error read message!  e: " + ex.Message.ToString());
			}
			if (!connected)
			{
				return;
			}
			if (messageHandler != null)
			{
				if (currentTimeMillis() - timeConnected > 500)
				{
					messageHandler.onDisconnected();
				}
				else
				{
					messageHandler.onConnectionFail();
				}
			}
			if (sc != null)
			{
				cleanNetwork();
			}
		}

		private void getKey(Message message)
		{
			try
			{
				sbyte b = message.reader().readSByte();
				key = new sbyte[b];
				for (int i = 0; i < b; i++)
				{
					key[i] = message.reader().readSByte();
				}
				for (int j = 0; j < key.Length - 1; j++)
				{
					key[j + 1] ^= key[j];
				}
				getKeyComplete = true;
			}
			catch (Exception)
			{
			}
		}

		private Message readMessage()
		{
			try
			{
				sbyte b = dis.ReadSByte();
				if (getKeyComplete)
				{
					b = readKey(b);
				}
				int num;
				if (getKeyComplete)
				{
					if (b == -39 || b == -101 || b == -93 || b == 76)
					{
						sbyte b2 = dis.ReadSByte();
						sbyte b3 = dis.ReadSByte();
						sbyte b4 = dis.ReadSByte();
						sbyte b5 = dis.ReadSByte();
						num = ((readKey(b2) & 0xFF) << 24) | ((readKey(b3) & 0xFF) << 16) | ((readKey(b4) & 0xFF) << 8) | (readKey(b5) & 0xFF);
					}
					else
					{
						sbyte b6 = dis.ReadSByte();
						sbyte b7 = dis.ReadSByte();
						num = ((readKey(b6) & 0xFF) << 8) | (readKey(b7) & 0xFF);
					}
				}
				else if (b == -39)
				{
					num = dis.ReadInt32();
				}
				else
				{
					sbyte num2 = dis.ReadSByte();
					sbyte b8 = dis.ReadSByte();
					num = (num2 & 0xFF00) | (b8 & 0xFF);
				}
				sbyte[] array = new sbyte[num];
				Buffer.BlockCopy(dis.ReadBytes(num), 0, array, 0, num);
				recvByteCount += 5 + num;
				int num3 = recvByteCount + sendByteCount;
				strRecvByteCount = num3 / 1024 + "." + num3 % 1024 / 102 + "Kb";
				if (getKeyComplete)
				{
					for (int i = 0; i < array.Length; i++)
					{
						array[i] = readKey(array[i]);
					}
				}
				return new Message(b, array);
			}
			catch (Exception ex)
			{
				Debug.Log(ex.StackTrace.ToString());
			}
			return null;
		}
	}

	protected static Session_ME instance = new Session_ME();

	private static NetworkStream dataStream;

	private static BinaryReader dis;

	private static BinaryWriter dos;

	public static IMessageHandler messageHandler;

	private static TcpClient sc;

	public static bool connected;

	public static bool connecting;

	public static bool isStart;

	private static Sender sender = new Sender();

	public static Thread initThread;

	public static Thread collectorThread;

	public static Thread sendThread;

	public static int sendByteCount;

	public static int recvByteCount;

	private static bool getKeyComplete;

	public static sbyte[] key = null;

	private static sbyte curR;

	private static sbyte curW;

	private static int timeConnected;

	public static string strRecvByteCount = "";

	public static bool isCancel;

	private string host;

	private int port;

	public static long timeStart = 0L;

	private static string test = "";

	public static mVector recieveMsg = new mVector();

	public Session_ME()
	{
		mSystem.outz("init Session_ME");
	}

	public void clearSendingMessage()
	{
		sender.sendingMessage.Clear();
	}

	public static Session_ME gI()
	{
		if (instance == null)
		{
			instance = new Session_ME();
		}
		return instance;
	}

	public bool isConnected()
	{
		return connected;
	}

	public void setHandler(IMessageHandler msgHandler)
	{
		messageHandler = msgHandler;
	}

	public void connect(string host, int port)
	{
		if (!connected && !connecting)
		{
			this.host = host;
			this.port = port;
			getKeyComplete = false;
			sc = null;
			initThread = new Thread(NetworkInit);
			initThread.Start();
		}
	}

	private void NetworkInit()
	{
		isCancel = false;
		connecting = true;
		Thread.CurrentThread.Priority = System.Threading.ThreadPriority.Highest;
		connected = true;
		try
		{
			doConnect(host, port);
			messageHandler.onConnectOK();
		}
		catch (Exception)
		{
			if (messageHandler != null)
			{
				close();
				messageHandler.onConnectionFail();
			}
		}
	}

	public void doConnect(string host, int port)
	{
		try
		{
			isStart = true;
			timeStart = GameCanvas.getTime();
			sc = new TcpClient();
			sc.Connect(host, port);
			dataStream = sc.GetStream();
			isStart = false;
			dis = new BinaryReader(dataStream, new UTF8Encoding());
			dos = new BinaryWriter(dataStream, new UTF8Encoding());
			new Thread(sender.run).Start();
			collectorThread = new Thread(new MessageCollector().run);
			collectorThread.Start();
			timeConnected = currentTimeMillis();
			connecting = false;
			doSendMessage(new Message((sbyte)(-27)));
		}
		catch (Exception)
		{
			if (messageHandler != null)
			{
				messageHandler.onConnectionFail();
			}
		}
	}

	public void sendMessage(Message message)
	{
		sender.AddMessage(message);
	}

	private static void doSendMessage(Message m)
	{
		sbyte[] data = m.getData();
		try
		{
			test = 1 + " " + m.command;
			if (getKeyComplete)
			{
				sbyte value = writeKey(m.command);
				dos.Write(value);
			}
			else
			{
				dos.Write(m.command);
			}
			if (data != null)
			{
				test = 2 + " " + m.command;
				int num = data.Length;
				if (getKeyComplete)
				{
					int num2 = writeKey((sbyte)(num >> 8));
					dos.Write((sbyte)num2);
					int num3 = writeKey((sbyte)(num & 0xFF));
					dos.Write((sbyte)num3);
				}
				else
				{
					dos.Write((ushort)num);
				}
				test = 3 + " " + m.command;
				if (getKeyComplete)
				{
					for (int i = 0; i < data.Length; i++)
					{
						sbyte value2 = writeKey(data[i]);
						dos.Write(value2);
					}
				}
				test = 4 + " " + m.command;
				sendByteCount += 5 + data.Length;
			}
			else
			{
				test = 5 + " " + m.command;
				if (getKeyComplete)
				{
					int num4 = writeKey((sbyte)0);
					dos.Write((sbyte)num4);
					int num5 = writeKey((sbyte)0);
					dos.Write((sbyte)num5);
				}
				else
				{
					dos.Write((ushort)0);
				}
				sendByteCount += 5;
				test = 6 + " " + m.command;
			}
			dos.Flush();
			test = 7 + " " + m.command;
		}
		catch (Exception ex)
		{
			mSystem.outz("ERROR SEND MSG  e: " + ex.StackTrace + "   , command : " + m.command);
		}
	}

	public static sbyte readKey(sbyte b)
	{
		sbyte result = (sbyte)((key[curR++] & 0xFF) ^ (b & 0xFF));
		if (curR >= key.Length)
		{
			curR %= (sbyte)key.Length;
		}
		return result;
	}

	public static sbyte writeKey(sbyte b)
	{
		sbyte result = (sbyte)((key[curW++] & 0xFF) ^ (b & 0xFF));
		if (curW >= key.Length)
		{
			curW %= (sbyte)key.Length;
		}
		return result;
	}

	public static void onRecieveMsg(Message msg)
	{
		if (Thread.CurrentThread.Name == Main.mainThreadName)
		{
			messageHandler.onMessage(msg);
		}
		else
		{
			recieveMsg.addElement(msg);
		}
	}

	public static void update()
	{
		while (recieveMsg.size() > 0)
		{
			Message message = (Message)recieveMsg.elementAt(0);
			if (message == null)
			{
				recieveMsg.removeElementAt(0);
				break;
			}
			messageHandler.onMessage(message);
			recieveMsg.removeElementAt(0);
		}
	}

	public void close()
	{
		recieveMsg.removeAllElements();
		cleanNetwork();
		isStart = false;
		messageHandler = null;
	}

	private static void cleanNetwork()
	{
		key = null;
		curR = 0;
		curW = 0;
		try
		{
			connected = false;
			connecting = false;
			if (sc != null)
			{
				sc.Close();
				sc = null;
			}
			if (dataStream != null)
			{
				dataStream.Close();
				dataStream = null;
			}
			if (dos != null)
			{
				dos.Close();
				dos = null;
			}
			if (dis != null)
			{
				dis.Close();
				dis = null;
			}
			sendThread = null;
			collectorThread = null;
		}
		catch (Exception)
		{
		}
	}

	public static int currentTimeMillis()
	{
		return Environment.TickCount;
	}

	public static byte convertSbyteToByte(sbyte var)
	{
		if (var > 0)
		{
			return (byte)var;
		}
		return (byte)(var + 256);
	}

	public static byte[] convertSbyteToByte(sbyte[] var)
	{
		byte[] array = new byte[var.Length];
		for (int i = 0; i < var.Length; i++)
		{
			if (var[i] > 0)
			{
				array[i] = (byte)var[i];
			}
			else
			{
				array[i] = (byte)(var[i] + 256);
			}
		}
		return array;
	}
}

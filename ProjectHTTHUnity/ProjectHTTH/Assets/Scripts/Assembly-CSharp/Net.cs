using UnityEngine;

internal class Net
{
	public static WWW www;

	public static IKAction h;

	public static void update()
	{
		if (www != null && www.isDone)
		{
			string text = "";
			if (www.error == null || www.error.Equals(""))
			{
				text = www.text;
			}
			www = null;
			if (h != null)
			{
				h.perform(text);
			}
		}
	}

	public static void connectHTTP(string link, IKAction h)
	{
		if (www != null)
		{
			Cout.LogError("GET HTTP BUSY");
		}
		Debug.Log("REQUEST " + link);
		www = new WWW(link);
		Net.h = h;
		Debug.Log(www?.ToString() + " @@@@@");
	}
}

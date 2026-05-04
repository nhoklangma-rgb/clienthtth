using System;

public class ipKeyboard
{
    private static UnityEngine.TouchScreenKeyboard tk;

    public static int TEXT;

    public static int NUMBERIC = 1;

    public static int PASS = 2;

    private static IKAction act;

    public static void openKeyBoard(string caption, int type, string text, IKAction action)
    {
        act = action;
        TouchScreenKeyboardType t = ((type == 0 || type == 2) ? TouchScreenKeyboardType.ASCIICapable : TouchScreenKeyboardType.NumberPad);
        TouchScreenKeyboard.hideInput = false;
        tk = TouchScreenKeyboard.Open(text, t, false, false, type == 2, false, caption);
    }

    public static void update()
    {
        try
        {
            if (tk != null && tk.status == UnityEngine.TouchScreenKeyboard.Status.Done)
            {
                if (act != null)
                {
                    act.perform(tk.text);
                }
                tk.text = string.Empty;
                tk = null;
            }
        }
        catch (Exception)
        {
        }
    }
}

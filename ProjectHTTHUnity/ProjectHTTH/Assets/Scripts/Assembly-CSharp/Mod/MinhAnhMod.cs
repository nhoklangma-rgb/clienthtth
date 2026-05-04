namespace Mod
{
	public class MinhAnhMod : MainScreen
	{
		public static MinhAnhMod gI()
		{
			if (MinhAnhMod.instance == null)
			{
				MinhAnhMod.instance = new MinhAnhMod();
			}
			return MinhAnhMod.instance;
		}
		public static MinhAnhMod instance;
		public static bool isTanSat;

		public static bool isGomQuai;

		public static bool isXoaDiaHinh;
		public void MenuMod()
		{
			mVector mVector = new mVector();
			mVector.addElement(new iCommand(isTanSat ? "Tàn Sát: Bật" : "Tàn Sát: Tắt", 1, this));
			mVector.addElement(new iCommand(isTanSat ? "Gom Quái: Bật" : "Gom Quái: Tắt", 2, this));
			mVector.addElement(new iCommand(isTanSat ? "Xóa Địa Hình: Bật" : "Xóa Địa Hình: Tắt", 3, this));
			GameCanvas.menu.startAt(mVector, 2, "Mod");
		}
		public override void commandPointer(int index, int subIndex)
		{
			switch (index)
			{
				case 1:
					GlobalService.gI().chatPopup("Ok");
					return;
				default:
					return;
			}
		}
		public override void Show()
		{
			base.Show();
		}
		public override void updatekey()
		{
			base.updatekey();
		}
		public override void updatePointer()
		{
			base.updatePointer();
		}
	}
}
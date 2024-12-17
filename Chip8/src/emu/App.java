package emu;

import chip.Chip;

public class App extends Thread {
	
	private Chip chip8;
	private ChipFrame frame;

	public App() {
		chip8 = new Chip();
		chip8.init();
		chip8.loadProgram("ROM/pong.rom");

		frame = new ChipFrame(chip8);
	}
	
	public void run() {
		// Chip Updates at 60HZ, roughly 60 updates per second
		while (true) {
			chip8.setKeyBuffer(frame.getKeyBuffer());
			chip8.run();
			if (chip8.needsRedraw()) {
				frame.repaint();
				chip8.resetDrawFlag();
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		App main = new App();
		main.run();
	}
}

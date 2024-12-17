package chip;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import java.io.File;

public class Chip {
	/*
	 * Chip-8's memory access up to 4KB (4096 bytes) of memory from 0x000 to 0xFFF
	 * From 0x000 to 0x1FF is the original interpreted and should not be used for
	 * programs
	 * 
	 * Most Chip-8's programs begin at 0x200 (512)
	 */

	private char[] memory;

	/*
	 * Chip-8 has a 16 general purpose, 16-bits, register called Vx, where x is a
	 * hex digit from 0-F There is a 16-bit register known as I, which is used to
	 * store memory addresses, so only the 12 first bits are used.
	 * 
	 * VF should not be used to store any programs, as its mostly used as a Flag
	 */
	private char[] V;

	private char I;

	/*
	 * Two special purpose registers which are automatically decremented at a rate
	 * of 60Hz (1Hz = 1s)
	 */
	private int delay_timer;

	private int sound_timer;

	/*
	 * PC (Program Counter) is 16-bit and is used to store the currently executing
	 * address
	 */
	private char pc;

	/*
	 * The stack pointer is 8-bit and is used to point at the topmost level of the
	 * stack
	 */

	private byte stackPointer;

	/*
	 * The stack is an array of 16 x 16, 16-bit values, used to store the address
	 * the interpreter returns to when finished with a subroutine. Chip-8 allows for
	 * 16 nested subroutines
	 */
	private char[] stack;

	/*
	 * The computers that originally used Chip-8 had a 16-key hexadecimal keypad
	 */
	private byte[] keys;

	/*
	 * Chip-8 used a 64 x 32-pixel monochrome display (when representing use 640 x
	 * 320, otherwise it'll be too small.
	 * 
	 * Chip-8 sprites are up to 15 bytes long, possible size of 8 x 15
	 */
	private byte[] display;

	private boolean needsRedraw;

	public void init() {
		memory = new char[4096];
		pc = 0x200;

		V = new char[16];
		I = 0x0;

		delay_timer = 0;
		sound_timer = 0;

		keys = new byte[16];

		stack = new char[16 * 16];
		stackPointer = 0;

		display = new byte[64 * 32];

		needsRedraw = false;
		loadFontset();
	}

	// NNN: address
	// NN: 8-bit constant
	// N: 4-bit constant
	// X and Y: 4-bit register identifier
	// PC : Program Counter
	// I : 12bit register (For memory address) (Similar to void pointer)
	// VN: One of the 16 available variables. N may be 0 to F (hexadecimal);

	public void run() {
		// Fetch opcode. We shift it 8 bits since the opcodes are 2 bytes long, if we
		// didn't, they'd overlap
		char opcode = (char) ((memory[pc] << 8) | memory[pc + 1]);
		System.out.print(Integer.toHexString(opcode) + ": ");

		// Decode opcode
		switch (opcode & 0xF000) {
		case 0x0000: {
			// Multicase
			switch (opcode & 0x00FF) {
			case 0x00E0: //Clears the display
				for (int i = 0; i < display.length; i++) {
					display[i] = 0;
				}
				pc += 2;
				needsRedraw = true;
				break;
			case 0x00EE: // Return from a subroutine.
			{

				stackPointer--;
				// Returns from subroutine
				// If it didn't have +2, it'd do the subroutine AGAIN
				pc = (char) (stack[stackPointer] + 2);
				System.out.println("Returning to subroutine " + Integer.toHexString(pc).toUpperCase());
				break;
			}

			}
			break;
		}

		case 0x1000: // Jump to location nnn.
		{
			int nnn = (opcode & 0x0FFF);
			pc = (char) nnn;
			System.out.println("Jumping to location " + Integer.toHexString(nnn));
			break;
		}

		case 0x2000: // Call subroutine at nnn.
		{
			char address = (char) (opcode & 0x0FFF);
			stack[stackPointer] = pc;
			stackPointer++;
			pc = address;
			System.out.println("Calling subroutine at " + Integer.toHexString(pc).toUpperCase());
			break;
		}

		case 0x3000: // Skip next instruction if Vx = kk.
		{
			int x = ((opcode & 0x0F00) >> 8);
			int nn = (opcode & 0x00FF);

			if (V[x] == nn) {
				System.out.println("Skipping next instruction (V[" + Integer.toHexString(x) + "] = "
						+ Integer.toHexString(V[x]) + " == " + Integer.toHexString(nn) + ")");
				pc += 4;
			} else {
				System.out.println("Not skipping next instruction (V[" + Integer.toHexString(x) + "] = "
						+ Integer.toHexString(V[x]) + " == " + Integer.toHexString(nn) + ")");
				pc += 2;
			}
			break;
		}

		case 0x4000: // Skip next instruction if Vx != kk.
		{
			int x = (char) ((opcode & 0x0F00) >> 8);
			int nn = (char) (opcode & 0x00FF);

			if (V[x] != nn) {
				System.out.println("V[" + x + "] = " + V[x] + " != " + nn);
				pc += 4;
			} else {
				System.out.println("V[" + x + "] = " + V[x] + " == " + nn);
				pc += 2;
			}
			break;

		}

		case 0x5000: // Skip next instruction if Vx = Vy.
		{
			int x = (opcode & 0x0F00) >> 8;
			int y = (opcode & 0x00F0) >> 4;

			if (V[x] == V[y]) {
				System.out.println("Skipping next instruction (V[" + Integer.toHexString(x) + "] = " + V[x] + " == (V["
						+ Integer.toHexString(y) + "] = " + V[y] + ")");
				pc += 4;
			} else {
				System.out.println("Not skipping next instruction (V[" + Integer.toHexString(x) + "] = " + V[x]
						+ " == (V[" + Integer.toHexString(y) + "] = " + V[y] + ")");
				pc += 2;
			}
			break;
		}

		case 0x6000: // Set Vx = NN
		{
			int x = ((opcode & 0x0F00) >> 8);
			char nn = (char) (opcode & 0x00FF);
			V[x] = nn;
			pc += 2;
			System.out.println("Setting V" + Integer.toHexString(x).toUpperCase() + " to " + (opcode & 0x00FF));
			break;
		}

		case 0x7000: // Set Vx = Vx + kk.
		{
			int x = ((opcode & 0x0F00) >> 8);
			int nn = (opcode & 0x00FF);
			System.out.println("Adding V" + Integer.toHexString(x).toUpperCase() + "(" + (Integer.toHexString(V[x]))
					+ ") + " + Integer.toHexString(nn) + " = " + (V[x] + nn));
			V[x] = (char) ((V[x] + nn) & 0xFF);
			pc += 2;
			break;
		}

		case 0x8000: {
			switch (opcode & 0x000F) {
			case 0x0000: // Set Vx = Vy.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				int y = (char) ((opcode & 0x00F0) >> 4);

				System.out.println("Storing value V[" + y + "] = " + Integer.toHexString(V[y]) + " into V[" + x + "]");

				V[x] = V[y];
				pc += 2;
				break;
			}
			case 0x0001: // Set Vx = Vx OR Vy.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				int y = (char) ((opcode & 0x00F0) >> 4);

				System.out.println(
						"Set V[" + x + "] to " + (int) V[x] + " | " + (int) V[y] + " = " + (int) (V[x] | V[y]));
				V[x] = (char) ((V[x] | V[y]) & 0xFF);
				pc += 2;
				break;
			}

			case 0x0002: // Set Vx = Vx AND Vy.
			{
				int x = ((opcode & 0x0F00) >> 8);
				int y = ((opcode & 0x00F0) >> 4);

				System.out.println(
						"Set V[" + x + "] to " + (int) V[x] + " & " + (int) V[y] + " = " + (int) (V[x] & V[y]));
				V[x] = (char) ((V[x] & V[y]) & 0xFF);
				pc += 2;
				break;
			}

			case 0x0003: // Set Vx = Vx XOR Vy.
			{
				int x = ((opcode & 0x0F00) >> 8);
				int y = ((opcode & 0x00F0) >> 4);

				System.out.println(
						"Set V[" + x + "] to " + (int) V[x] + " ^ " + (int) V[y] + " = " + (int) (V[x] ^ V[y]));
				V[x] = (char) ((V[x] ^ V[y]) & 0xFF);
				pc += 2;
				break;
			}

			case 0x0004: // Set Vx = Vx + Vy, set VF = carry.
			{
				int x = ((opcode & 0x0F00) >> 8);
				int y = ((opcode & 0x00F0) >> 4);

				System.out.print("Adding V[" + x + "] to V[" + y + "] = " + ((V[x] + V[y] & 0xFF) + ","));

				if (V[y] > 0xFF - V[x]) {
					V[0xF] = 1;
					System.out.println("Carry!");
				} else {
					V[0xF] = 0;
					System.out.println("No Carry");
				}

				V[x] = (char) ((V[x] + V[y]) & 0xFF);
				pc += 2;
				break;
			}

			case 0x0005: // Set Vx = Vx - Vy, set VF = NOT borrow.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				int y = (char) ((opcode & 0x00F0) >> 4);

				System.out.print("Substracting V[" + y + "] to V[" + x + "] = " + ((V[x] - V[y] & 0xFF) + ", "));

				if (V[x] > V[y]) {
					V[0xF] = 1;
					System.out.println("No borrow");
				} else {
					V[0xF] = 0;
					System.out.println("Borrow!");
				}

				V[x] = (char) ((V[x] - V[y]) & 0x0FF);
				pc += 2;
				break;
			}

			case 0x0006: // Set Vx = Vx SHR 1.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				V[0xF] = (char) (V[x] & 0x01);
				V[x] = (char) (V[x] >> 1);
				pc += 2;
				System.out.println("Shift V[ " + x + "] >> 1 and VF to LSB of VX");
				break;
			}

			case 0x0007: // Set Vx = Vy - Vx, set VF = NOT borrow.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				int y = (char) ((opcode & 0x00F0) >> 4);

				System.out.print("Substracting V[" + x + "] to V[" + y + "] = " + ((V[y] - V[x] & 0xFF) + ", "));

				if (V[x] > V[y]) {
					V[0xF] = 0;
					System.out.println("Borrow");
				} else {
					V[0xF] = 1;
					System.out.println("No borrow!");
				}

				V[x] = (char) ((V[y] - V[x]) & 0x0FF);
				pc += 2;
				break;
			}

			case 0x000E: // Set Vx = Vx SHL 1.
			{
				int x = (char) ((opcode & 0x0F00) >> 8);
				V[0xF] = (char) (V[x] & 0x80);
				V[x] = (char) (V[x] << 1);
				pc += 2;
				System.out.println("Shift V[ " + x + "] << 1 and VF to MSB of VX");
				break;
			}
			}
			break;
		}

		case 0x9000: // Skip next instruction if Vx != Vy.
		{
			int x = (char) ((opcode & 0x0F00) >> 8);
			int y = (char) ((opcode & 0x00F0) >> 4);

			if (V[x] != V[y]) {
				System.out.println("Skipping instruction");
				pc += 4;
			} else {
				System.out.println("Not skipping instruction");
				pc += 2;
			}
		}

		case 0xA000: // Set I = NNN
		{
			char address = (char) (opcode & 0x0FFF);
			I = address;
			pc += 2;
			System.out.println("Set I to " + Integer.toHexString(address).toUpperCase());
			break;
		}

		case 0xB000: // Jump to location nnn + V0.
		{
			int address = (opcode & 0x0FFF);
			int extra = (V[0] & 0xFF);
			pc = (char) (address + extra);
			break;
		}

		case 0xC000: // Set Vx = random byte AND kk.
		{
			int x = ((opcode & 0x0F00) >> 8);
			int nn = ((opcode & 0x00FF));
			int randomNumber = new Random().nextInt(255) & nn;
			System.out.println("V[" + Integer.toHexString(x) + "] has been set to random number " + randomNumber);

			V[x] = (char) randomNumber;
			pc += 2;
			break;
		}

		case 0xD000: // Draws a sprite at coordinate (VX, VY) that has a width of 8 pixels and a
						// height of N pixels
		// Draw by XORing to the screen
		// Check colission in V[0xF]
		// Read image from I
		{
			int x = V[(opcode & 0x0F00) >> 8];
			int y = V[(opcode & 0x00F0) >> 4];
			int height = (opcode & 0x000F);

			V[0xF] = 0;

			for (int _y = 0; _y < height; _y++) {
				int line = memory[I + _y];
				for (int _x = 0; _x < 8; _x++) {
					int pixel = line & (0x80 >> _x);
					if (pixel != 0) {
						int totalX = x + _x;
						int totalY = y + _y;
						int index = ((totalY * 64) + totalX) % 2048;

						if (display[index] == 1) {
							V[0xF] = 1;
						}

						display[index] ^= 1;

					}
				}
			}
			pc += 2;
			needsRedraw = true;
			System.out.println("Drawing sprite at V[" + ((opcode & 0x0F00) >> 8) + "] - " + x + " V["
					+ ((opcode & 0x00F0) >> 4) + "] - " + y);
			break;
		}

		case 0xE000: {
			switch (opcode & 0x00FF) {
			case 0x009E: // Skip next instruction if key with the value of Vx is pressed.
			{
				int x = (opcode & 0x0F00) >> 8;
				int key = V[x];
				if (keys[key] == 1) {
					pc += 4;
				} else {
					pc += 2;
				}
				System.out.println("Skipping next instruction if V[" + x + "] = " + ((int) V[x]) + " is pressed");
				break;
			}

			case 0x00A1: // Skip next instruction if key with the value of Vx is not pressed.
			{
				int x = (opcode & 0x0F00) >> 8;
				int key = V[x];
				if (keys[key] == 0) {
					pc += 4;
				} else {
					pc += 2;
				}
				System.out.println("Skipping next instruction if V[" + x + "] = " + (int) V[x] + " is NOT pressed");
				break;
			}

			}
			break;
		}

		case 0xF000: {
			switch (opcode & 0x00FF) {
			case 0x0007: // Set Vx = delay timer value.
			{
				int x = (opcode & 0x0F00) >> 8;
				V[x] = (char) delay_timer;
				pc += 2;
				System.out.println("V[" + x + "] has been set to " + Integer.toHexString(V[x]));
				break;

			}

			case 0x000A: // Wait for a key press, store the value of the key in Vx.
			{
				int x = (opcode & 0x0F00) >> 8;
				for (int i = 0; i < keys.length; i++) {
					if (keys[i] == 1) {
						V[x] = (char) i;
						pc += 2;
						break;
					}
				}
				System.out.println("Awaiting key press to be stored in V[" + x + "]");
				break;
			}

			case 0x0015: // Set delay timer = Vx
			{
				int x = (opcode & 0x0F00) >> 8;
				delay_timer = V[x];
				pc += 2;
				System.out.println("Set delay_timer to V[" + x + "] = " + (int) V[x]);
				break;

			}

			case 0x0018: // Set sound timer = Vx.
			{
				int x = V[(opcode & 0x0F00) >> 8];
				sound_timer = x;
				pc += 2;
				System.out.println("Setting sound_timer value from " + sound_timer + " to " + Integer.toHexString(x));
				break;

			}

			case 0x001E: // Set I = I + Vx.
			{
				int x = (opcode & 0x0F00) >> 8;
				I = (char) (I + V[x]);
				System.out.println("Adding V[" + x + "] = " + (int) V[x] + " to I");
				pc += 2;
				break;
			}

			case 0x0029: // Set I = location of sprite for digit Vx.
			{
				int x = (opcode & 0x0F00) >> 8;
				int character = V[x];
				I = (char) (0x0050 + (character * 5));
				System.out.println("Setting I to Character V[" + x + "] = " + Integer.toHexString(V[x]) + " Offset to "
						+ Integer.toHexString(I).toUpperCase());
				pc += 2;
				break;
			}

			case 0x0033: // Store BCD representation of Vx in memory locations I, I+1, and I+2.
			{
				int x = V[(opcode & 0x0F00) >> 8];

				int hundreds = (x / 100);
				int tens = (x / 10) % 10;
				int ones = x % 10;

				memory[I] = (char) hundreds;
				memory[I + 1] = (char) tens;
				memory[I + 2] = (char) ones;

				System.out.println("Storing BCD V[" + ((opcode & 0x0F00) >> 8) + "] = "
						+ (int) (V[(opcode & 0x0F00) >> 8]) + " as {" + hundreds + ", " + tens + ", " + ones + "}");
				pc += 2;
				break;
			}

			case 0x0055: // Store registers V0 through Vx in memory starting at location I.
			{
				System.err.println("Unsupported Opcode!");
				System.exit(1);
				break;
			}

			case 0x0065: // Read registers V0 through Vx from memory starting at location I.
			// Llena valores de memoria desde V0 hasta VX empezando por I
			{
				int _x65 = ((opcode & 0x0F00) >> 8);

				for (int i = 0; i <= _x65; i++) {
					V[i] = memory[I + i];
				}
				System.out.println("Setting V[0] to V[" + _x65 + "] to the values of memory[0x"
						+ Integer.toHexString(I & 0xFFFF).toUpperCase() + "]");
				pc += 2;
				break;
			}

			}
			break;
		}

		default:
			System.err.println("Unsupported Opcode!");
			System.exit(1);
		}
		// Execute opcode

		if (sound_timer > 0) {
			sound_timer--;
		}
		if (delay_timer > 0) {
			delay_timer--;
		}
	}

	public byte[] getDisplay() {
		return display;
	}

	public boolean needsRedraw() {
		return needsRedraw;
	}

	public void resetDrawFlag() {
		needsRedraw = false;

	}

	/**
	 * Function used to read a .c8 file and extract its bytes, loading them into
	 * memory
	 * 
	 * @param file File to extract the bytes from
	 */
	public void loadProgram(String file) {
		DataInputStream input = null;
		try {
			input = new DataInputStream(new FileInputStream(new File(file)));

			int offset = 0;
			while (input.available() > 0) {
				memory[0x200 + offset] = (char) (input.readByte() & 0xFF);
				offset++;
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				input.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void loadFontset() {
		for (int i = 0; i < ChipData.fontset.length; i++) {
			memory[0x50 + i] = (char) (ChipData.fontset[i] & 0xFF);
		}
	}

	public void setKeyBuffer(int[] keyBuffer) {
		for (int i = 0; i < keyBuffer.length; i++) {
			keys[i] = (byte) keyBuffer[i];
		}

	}
}

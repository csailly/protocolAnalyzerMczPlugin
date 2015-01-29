package fr.nestof;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import nu.nethome.util.ps.PulseLength;

public class DecodeTimeV2 {
	private static final int IDLE = 0;

	private static final int READING_MESSAGE = 1;



	private static final int DATA_LENGTH = 12;

	private static final int NB_DATA_BY_MESSAGE = 7;

	private static final int MESSAGE_LENGTH = DATA_LENGTH * NB_DATA_BY_MESSAGE;

	private int state = DecodeTimeV2.IDLE;

	private final List<Long> datas = new ArrayList<Long>();

	private long data = 0;

	private int bitCounter = 0;

	private int repeatCount = 0;

	private double lastPulse;

	private boolean lastState = false;

	private int pulseIndex = 0;

	private String decodedDataValue = "";

	private String decodedMessageValue = "";

	private String lastDecodedMessageValue = "";

	public static final int SHORT_PULSE_LENGTH = 408;

	public static final int LONG_PULSE_LENGTH = 816;

	public static final int DATAS_SPACE_LENGTH = 1224;

	public static final PulseLength SHORT_PULSE = new PulseLength(DecodeTimeV2.class, "SHORT_MARK", SHORT_PULSE_LENGTH, 317, 544);

	public static final PulseLength LONG_PULSE = new PulseLength(DecodeTimeV2.class, "LONG_MARK", LONG_PULSE_LENGTH, 771, 907);

	public static final PulseLength DATAS_SPACE = new PulseLength(DecodeTimeV2.class, "DATAS_SPACE", DATAS_SPACE_LENGTH, 1134, 1315);

	public static final PulseLength MESSAGES_SPACE = new PulseLength(DecodeTimeV2.class, "MESSAGES_SPACE", 4580, 4580, 199000);

	public static final PulseLength TRAME_END_SPACE = new PulseLength(DecodeTimeV2.class, "MESSAGES_SPACE", 200000, 200000, 300000);

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(final String[] args) {

		final InputStream is = ClassLoader.getSystemResourceAsStream("decode1.txt");

		final DecodeTimeV2 decodeTime = new DecodeTimeV2();

		BufferedReader reader = null;

		try {

			reader = new BufferedReader(new InputStreamReader(is));
			String line = reader.readLine();
			while (line != null) {
				if (!line.startsWith("--")) {
					String[] tokens = line.split(" : ");
					final Double pulse = Double.parseDouble(tokens[0]);
					boolean state = ("true".equals(tokens[1]));

					decodeTime.parse(pulse, state);
				}
				line = reader.readLine();
			}
		} catch (final FileNotFoundException ex) {
			Logger.getLogger(DecodeTimeV2.class.getName()).log(Level.SEVERE, null, ex);
		} catch (final IOException ex) {
			Logger.getLogger(DecodeTimeV2.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			try {
				reader.close();
			} catch (final IOException ex) {
				Logger.getLogger(DecodeTimeV2.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

	}

	/**
	 * An internal helper function which collects decoded bits and assembles
	 * messages
	 * 
	 * @param b
	 *            a decoded bit
	 */
	private void addBit(final boolean b) {
		final int bit = b ? 1 : 0;

		decodedDataValue = decodedDataValue + bit;
		decodedMessageValue = decodedMessageValue + bit;

		data <<= 1;
		data |= bit;

		bitCounter++;

		// Check if a single data is receive
		if (bitCounter % (DecodeTimeV2.MESSAGE_LENGTH / DecodeTimeV2.NB_DATA_BY_MESSAGE) == 0) {
			log(" DATA : " + Long.toBinaryString(data) + " 0x" + Long.toHexString(data) + " " + data + " | ", false);

			log(decodedDataValue + " 0x" + Long.toHexString(Long.parseLong(decodedDataValue, 2)) + " " + Long.parseLong(decodedDataValue, 2), true);

			datas.add(data);
			data = 0;
		}

		// Check if this is a complete message
		if (bitCounter == DecodeTimeV2.MESSAGE_LENGTH) {

			log("Message : ", false);
			for (final Long data : datas) {
				log(" 0x" + Long.toHexString(data), false);
			}
			log("", true);

			// Check if this is a repeat
			if (decodedMessageValue.equals(lastDecodedMessageValue)) {
				repeatCount++;
			} else {
				repeatCount = 0;
			}

			// Report the parsed message
			lastDecodedMessageValue = decodedMessageValue;
			data = 0;
			bitCounter = 0;
			state = DecodeTimeV2.IDLE;
		}
	}

	public int parse(final double pulse, final boolean state) {
		switch (this.state) {
		case IDLE: {
			if (DATAS_SPACE.matches(pulse) && !state) {
				// Start message pulse
				pulseIndex = 1;
				this.state = READING_MESSAGE;
				if (SHORT_PULSE.matches(lastPulse)) {
					log("Start Trame " + pulse, true);
				} else {
					log("Start " + pulse, true);
				}
				log("b", false);
				decodedDataValue = "";
				decodedMessageValue = "";
				datas.clear();
			} else {
				log("" + pulse + " : " + state, true);
			}
			break;
		}
		case READING_MESSAGE: {
			if (lastState == state && pulse < 200000) {
				// 2 consecutive state must be different
				log("2 consecutive state must be different", true);
				break;
			}

			if (SHORT_PULSE.matches(pulse)) {
				// Single pulse
				if (!state) {
					log("b", false);
				} else {
					log("h", false);
				}
				pulseIndex++;
				if (pulseIndex % 2 == 0) {
					addBit(state);
				}
			} else if (LONG_PULSE.matches(pulse)) {
				// Double pulse
				if (!state) {
					if ((bitCounter + 1) % DATA_LENGTH != 0) {
						log("bb", false);
					} else {
						log("b", false);
					}
				} else {
					if ((bitCounter + 1) % DATA_LENGTH != 0) {
						log("hh", false);
					} else {
						log("h", false);
					}
				}
				pulseIndex += 2;
				if (pulseIndex % 2 == 1) {
					addBit(state);
				}
			} else if (DATAS_SPACE.matches(pulse) & !state) {
				// Datas separator pulse
				log("b", false);
				pulseIndex = 1;
				data = 0;
				decodedDataValue = "";
			} else if (MESSAGES_SPACE.matches(pulse)) {
				// End message pulse
				log("h", false);
				addBit(state);
				log("End " + pulse, true);

				pulseIndex = 0;
				decodedDataValue = "";
				decodedMessageValue = "";
				this.state = IDLE;
				data = 0;
				bitCounter = 0;
			} else if (TRAME_END_SPACE.matches(pulse)) {
				// End trame pulse
				log("h", false);
				addBit(!state);
				log("End " + pulse, true);

				pulseIndex = 0;
				decodedDataValue = "";
				decodedMessageValue = "";
				this.state = IDLE;
				data = 0;
				bitCounter = 0;
			} else {
				log("error" + pulse, true);
				pulseIndex = 0;
				decodedDataValue = "";
				decodedMessageValue = "";
				this.state = IDLE;
				data = 0;
				bitCounter = 0;
			}
			break;
		}
		}
		lastState = state;
		lastPulse = pulse;
		return this.state;
	}

	public void log(final String line, final boolean newLine) {
		if (newLine) {
			System.out.println(line);
		} else {
			System.out.print(line);
		}
	}
}
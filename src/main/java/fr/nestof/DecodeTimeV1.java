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

import nu.nethome.util.ps.FieldValue;
import nu.nethome.util.ps.ProtocolDecoderSink;
import nu.nethome.util.ps.ProtocolMessage;
import nu.nethome.util.ps.PulseLength;

public class DecodeTimeV1 {
	private static final int IDLE = 0;

	private static final int READING_MESSAGE = 1;

	private static final int MESSAGE_LENGTH = 84;

	private static final int NB_DATA_BY_MESSAGE = 7;

	private int state = DecodeTimeV1.IDLE;

	private final List<Long> datas = new ArrayList<Long>();

	private long data = 0;

	private int bitCounter = 0;

	private int repeatCount = 0;

	private final ProtocolDecoderSink sink = null;

	private double lastPulse;

	private boolean lastState = false;

	private int pulseIndex = 0;

	private String decodedDataValue = "";

	private String decodedMessageValue = "";

	private String lastDecodedMessageValue = "";

	public static final int SHORT_PULSE_LENGTH = 408;

	public static final int LONG_PULSE_LENGTH = 816;

	public static final int DATAS_SPACE_LENGTH = 1224;

	public static final double START_TRAME_LENGTH = 199000.0;

	public static final PulseLength SHORT_PULSE = new PulseLength(DecodeTimeV1.class, "SHORT_MARK", SHORT_PULSE_LENGTH, 272, 612);

	public static final PulseLength LONG_PULSE = new PulseLength(DecodeTimeV1.class, "LONG_MARK", LONG_PULSE_LENGTH, 635, 930);

	public static final PulseLength DATAS_SPACE_PULSE = new PulseLength(DecodeTimeV1.class, "DATAS_SPACE", DATAS_SPACE_LENGTH, 1179, 1429);

	public static final PulseLength MESSAGES_SPACE_PULSE= new PulseLength(DecodeTimeV1.class, "MESSAGES_SPACE", 5150, 5079, 199000);


	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(final String[] args) {

		final InputStream is = ClassLoader.getSystemResourceAsStream("6.txt");

		final DecodeTimeV1 decodeTime = new DecodeTimeV1();

		BufferedReader reader = null;

		try {

			reader = new BufferedReader(new InputStreamReader(is));
			String line = reader.readLine();
			while (line != null) {
				if (!line.startsWith("--")) {

					boolean state = line.startsWith("m");
					final Double pulse = Double.parseDouble(line.replaceFirst("s", "").replaceFirst("m", ""));

					decodeTime.parse(pulse, state);
				}
				line = reader.readLine();
			}
		} catch (final FileNotFoundException ex) {
			Logger.getLogger(DecodeTimeV1.class.getName()).log(Level.SEVERE, null, ex);
		} catch (final IOException ex) {
			Logger.getLogger(DecodeTimeV1.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			try {
				reader.close();
			} catch (final IOException ex) {
				Logger.getLogger(DecodeTimeV1.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

	}

	/**
	 * An internal helper function which collects decoded bits and assembles
	 * messages
	 * 
	 * @param b a decoded bit
	 */
	private void addBit(final boolean b) {
		final int bit = b ? 1 : 0;

		decodedDataValue = decodedDataValue + bit;
		decodedMessageValue = decodedMessageValue + bit;

		data <<= 1;
		data |= bit;

		bitCounter++;

		// Check if a single data is receive
		if (bitCounter % (MESSAGE_LENGTH / NB_DATA_BY_MESSAGE) == 0) {
			log(" DATA : " + Long.toBinaryString(data) + " 0x" + Long.toHexString(data) + " " + data, true);


			datas.add(data);
			data = 0;
		}

		// Check if this is a complete message
		if (bitCounter == MESSAGE_LENGTH) {
			String hexMessage = "";

			for (final Long data : datas) {
				hexMessage += "0x" + Long.toHexString(data) + " ";
			}


			// It is, read the parameters
			final int command = 0;
			final int address = 0;

			// Create the message
			final ProtocolMessage message = new ProtocolMessage("MCZ V1", command, address, 7);
			for (int i = 0; i < 7; i++) {
				message.setRawMessageByteAt(i, datas.get(i).intValue());
			}

			message.addField(new FieldValue("Message", hexMessage));

			// Check if this is a repeat
			if (decodedMessageValue.length() != 0 && decodedMessageValue.equals(lastDecodedMessageValue)) {
				repeatCount++;
				message.setRepeat(repeatCount);
			}

			// Report the parsed message
			//TODO sink.parsedMessage(message);
			lastDecodedMessageValue = decodedMessageValue;
			data = 0;
			bitCounter = 0;
			state = IDLE;

			log("Message : " + hexMessage, false);
			log(" - RepeatCount : " +  repeatCount, true);
		}

	}

	public int parse(final double pulse, final boolean state) {

		switch (this.state) {
		case IDLE: {
			if (START_TRAME_LENGTH == pulse){
				//Start Trame
				log("Start Trame " + pulse + " : " + state, true);
				decodedDataValue = "";
				decodedMessageValue = "";
				datas.clear();
				data = 0;
				pulseIndex = 0;
				bitCounter = 0;
				repeatCount = 0;
			}else if (DATAS_SPACE_PULSE.matches(pulse) && MESSAGES_SPACE_PULSE.matches(lastPulse) && state) {
				// Start message pulse
				pulseIndex = 1;
				this.state = DecodeTimeV1.READING_MESSAGE;
				log("Start " + pulse + " : " + state, true);
				log("h", false);
				decodedDataValue = "";
				//				decodedMessageValue = "";
				datas.clear();
			} else {
				log(pulse + " : " + state, true);
			}
			break;
		}
		case READING_MESSAGE: {
			if (lastState == state) {
				// 2 consecutive state must be differents
				log("Error 2 consecutive state must be differents " + pulse + " : " + state, true);
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
					addBit(!state);
				}
			} else if (LONG_PULSE.matches(pulse)) {
				// Double pulse
				if (!state) {
					log("bb", false);
				} else {
					log("hh", false);
				}
				pulseIndex += 2;
				if (pulseIndex % 2 == 1) {
					addBit(!state);
				}
			} else if (DATAS_SPACE_PULSE.matches(pulse) & state) {
				// Datas separator pulse
				log("h", false);
				pulseIndex = 1;
				data = 0;
				decodedDataValue = "";
			} else if (MESSAGES_SPACE_PULSE.matches(pulse)) {
				// End message pulse
				log("b", false);
				addBit(!state);
				log("End " + pulse + " : " + state, true);
				pulseIndex = 0;
				decodedDataValue = "";
				decodedMessageValue = "";
				this.state = DecodeTimeV1.IDLE;
				data = 0;
				bitCounter = 0;
			} else {
				log("Error " + pulse + " : " + state, true);
				pulseIndex = 0;
				decodedDataValue = "";
				decodedMessageValue = "";
				this.state = DecodeTimeV1.IDLE;
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

package fr.nestof;

/**
 * Copyright (C) 2005-2013, Stefan Strömberg <stefangs@nethome.nu>
 *
 * This file is part of OpenNetHome (http://www.nethome.nu).
 *
 * OpenNetHome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenNetHome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import nu.nethome.util.plugin.Plugin;
import nu.nethome.util.ps.FieldValue;
import nu.nethome.util.ps.ProtocolDecoder;
import nu.nethome.util.ps.ProtocolDecoderSink;
import nu.nethome.util.ps.ProtocolInfo;
import nu.nethome.util.ps.ProtocolMessage;
import nu.nethome.util.ps.PulseLength;

@Plugin
public class MCZDecoderV2 implements ProtocolDecoder {

	private static final int IDLE = 0;

	private static final int READING_MESSAGE = 1;

	private static final int MESSAGE_LENGTH = 84;

	private static final int NB_DATA_BY_MESSAGE = 7;

	private int state = MCZDecoderV2.IDLE;

	private final List<Long> datas = new ArrayList<Long>();

	private long data = 0;

	private int bitCounter = 0;

	private int repeatCount = 0;

	private ProtocolDecoderSink sink = null;

	private double lastPulse;

	FileOutputStream fos = null;

	BufferedWriter writer = null;

	private boolean lastState = false;

	private int pulseIndex = 0;

	private String decodedDataValue = "";

	private String decodedMessageValue = "";

	private String lastDecodedMessageValue = "";

	private static final String LOG_FILE = "c:/decode.txt";

	public static final PulseLength SHORT_PULSE = new PulseLength(MCZDecoderV2.class, "SHORT_MARK", 408, 317, 544);
	
	public static final PulseLength LONG_PULSE = new PulseLength(MCZDecoderV2.class, "LONG_MARK", 816, 771, 907);
	
	public static final PulseLength DATAS_SPACE = new PulseLength(MCZDecoderV2.class, "DATAS_SPACE", 1224, 1134, 1315);


	public MCZDecoderV2() {
		try {
			fos = new FileOutputStream(LOG_FILE);
			fos.close();
		} catch (final IOException e) {
			Logger.getLogger(MCZDecoderV2.class.getName()).log(Level.SEVERE, null, e);
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
		if (bitCounter % (MCZDecoderV2.MESSAGE_LENGTH / MCZDecoderV2.NB_DATA_BY_MESSAGE) == 0) {
			// log(" DATA : " + Long.toBinaryString(data) + " 0x"
			// + Long.toHexString(data) + " " + data + " | ", false);
			//
			// log(decodedDataValue + " 0x"
			// + Long.toHexString(Long.parseLong(decodedDataValue, 2))
			// + " " + Long.parseLong(decodedDataValue, 2), true);

			datas.add(data);
			data = 0;
		}

		// Check if this is a complete message
		if (bitCounter == MCZDecoderV2.MESSAGE_LENGTH) {
			String hexMessage = "";

			for (final Long data : datas) {
				hexMessage += "0x" + Long.toHexString(data) + " ";
			}
			if (repeatCount == 0) {
				log("Message : " + hexMessage, true);
			}

			// Create the message
			final ProtocolMessage message = new ProtocolMessage("MCZ V2", 2, 6, 7);
			for (int i = 0; i < 7; i++) {
				message.setRawMessageByteAt(i, datas.get(i).intValue());
			}

			message.addField(new FieldValue("Message", hexMessage));

			// Check if this is a repeat
			if (decodedMessageValue.equals(lastDecodedMessageValue)) {
				repeatCount++;
				message.setRepeat(repeatCount);
			} else {
				repeatCount = 0;
			}

			// Report the parsed message
			sink.parsedMessage(message);
			lastDecodedMessageValue = decodedMessageValue;
			data = 0;
			bitCounter = 0;
			state = MCZDecoderV2.IDLE;
		}

	}

	/**
	 * This is called by the framework to get information about the decoder
	 * 
	 * @return information about the decoder
	 */
	public ProtocolInfo getInfo() {

		return new ProtocolInfo("MCZ V2", "Manchester Reversed", "Mcz", MCZDecoderV2.MESSAGE_LENGTH, 5);
	}

	/**
	 * This is the method which the framework constantly feeds with pulses from
	 * the receiver. The decoder has to implement a state machine which
	 * interprets the pulse train and decodes its messages.
	 * 
	 * @param pulse
	 *            Length of the pulse in micro seconds
	 * @param state
	 *            true for a mark pulse and false for a space pulse
	 * @return the internal state of the decoder after decoding the pulse.
	 */
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
					if ((bitCounter + 1) % 12 != 0) {
						log("bb", false);
					} else {
						log("b", false);
					}
				} else {
					if ((bitCounter + 1) % 12 != 0) {
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
			} else if (pulse >= 4580 && pulse < 200000) {
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
			} else if (pulse >= 200000) {
				// End message pulse
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

	/**
	 * This is called by the framework to inform the decoder of the current sink
	 * 
	 * @param sink
	 */
	public void setTarget(final ProtocolDecoderSink sink) {
		this.sink = sink;
	}

	private void log(final String line, final boolean newLine) {
		try {
			fos = new FileOutputStream(LOG_FILE, true);
			writer = new BufferedWriter(new OutputStreamWriter(fos));
			writer.write(line);
			if (newLine) {
				writer.newLine();
			}
		} catch (final IOException e) {
			Logger.getLogger(MCZDecoderV2.class.getName()).log(Level.SEVERE, null, e);
		}

		try {
			writer.close();
			fos.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
}
/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/* OOOGG-JOrbis
 * Copyright (C) 2018 Allan Taborda dos Santos
 *  
 * Written by: 2018 Allan Taborda dos Santos <allan-taborda@bol.com.br>
 *   
 * Many thanks to 
 *   Monty <monty@xiph.org> and 
 *   The XIPHOPHORUS Company http://www.xiph.org/ .
 * JOrbis has been based on their awesome works, Vorbis codec.
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.allantaborda.ooogg_jorbis;

import com.allantaborda.ooogg.OggPackable;
import com.allantaborda.ooogg.OggPacket;
import com.allantaborda.ooogg.OggUtils;
import com.allantaborda.ooogg.Tags;
import com.allantaborda.ooogg.spi.OggAudioInputStream;
import com.allantaborda.ooogg.spi.OggFormatProvider;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class VorbisFormatProvider extends OggFormatProvider{
	public static final String FILE_HEADER = new String(new byte[]{(byte) 1, 'v', 'o', 'r', 'b', 'i', 's'});
	public static final String TAGS_HEADER = new String(new byte[]{(byte) 3, 'v', 'o', 'r', 'b', 'i', 's'});
	public static final AudioFormat.Encoding VORBIS_ENC = new AudioFormat.Encoding("VORBIS");
	public static final Type VORBIS_TYPE = new Type("VORBIS", "ogg");

	public VorbisFormatProvider(){
		super(true, false);
	}

	public Type getType(){
		return VORBIS_TYPE;
	}

	public AudioFormat.Encoding getEncoding(){
		return VORBIS_ENC;
	}

	public Tags getTags(){
		return new Tags(TAGS_HEADER, true);
	}

	public OggPackable getHeader(OggPacket packet){
		return new VorbisHeader(packet);
	}

	public OGGAudioFileFormat getAudioFileFormat(OggPackable header, long length, Map<String, Object> afProps, Map<String, Object> affProps){
		VorbisHeader h = (VorbisHeader) header;
		afProps.put("channels", (int) h.channels);
		afProps.put("sampleRate", h.sampleRate);
		afProps.put("maxBitrate", h.bitrateUpper);
		afProps.put("nominalBitrate", h.bitrateNominal);
		afProps.put("minBitrate", h.bitrateLower);
		afProps.put("frameRate", h.getFrameRate());
		return new OGGAudioFileFormat(VORBIS_TYPE, new AudioFormat(VORBIS_ENC, h.sampleRate, AudioSystem.NOT_SPECIFIED, h.channels, 1, h.getFrameRate(), false, afProps), length, affProps);
	}

	public OggAudioInputStream getAudioInputStream(AudioFormat trgFormat, AudioInputStream srcStream) throws IOException{
		return new VorbisAudioInputStream(trgFormat, srcStream);
	}

	private class VorbisHeader implements OggPackable{
		float sampleRate;
		int bitrateUpper, bitrateNominal, bitrateLower;
		byte channels, blocksizes;

		public VorbisHeader(OggPacket packet){
			if(packet.getSize() == 30 && packet.headerMatches(VorbisFormatProvider.FILE_HEADER) &&
					OggUtils.getIntFromByteArray(packet.getContent(), 7) == 0 && packet.getContent()[29] == 1){
				channels = packet.getContent()[11];
				sampleRate = OggUtils.getIntFromByteArray(packet.getContent(), 12);
				bitrateUpper = OggUtils.getIntFromByteArray(packet.getContent(), 16);
				bitrateNominal = OggUtils.getIntFromByteArray(packet.getContent(), 20);
				bitrateLower = OggUtils.getIntFromByteArray(packet.getContent(), 24);
				blocksizes = packet.getContent()[28];
			}else channels = -1;
		}

		float getFrameRate(){
			if(bitrateNominal > 0) return bitrateNominal / 8;
			else if(bitrateLower > 0) return bitrateLower / 8;
			return -1;
		}

		public boolean isValid(){
			return channels > 0 && sampleRate > 0;
		}

		public OggPacket toOggPacket(){
			byte[] content = new byte[30];
			for(int c = 0; c < 7; c++) content[c] = (byte) VorbisFormatProvider.FILE_HEADER.charAt(c);
			System.arraycopy(OggUtils.getByteArrayFromInt(0), 0, content, 7, 4);
			content[11] = channels;
			System.arraycopy(OggUtils.getByteArrayFromInt((int) sampleRate), 0, content, 12, 4);
			System.arraycopy(OggUtils.getByteArrayFromInt(bitrateUpper), 0, content, 16, 4);
			System.arraycopy(OggUtils.getByteArrayFromInt(bitrateNominal), 0, content, 20, 4);
			System.arraycopy(OggUtils.getByteArrayFromInt(bitrateLower), 0, content, 24, 4);
			content[28] = blocksizes;
			content[29] = (byte) 1;
			return new OggPacket(content);
		}
	}

	private class VorbisAudioInputStream extends OggAudioInputStream{
		Block vb;
		DspState vd;
		float[][][] pcmx;
		int[] index;

		VorbisAudioInputStream(AudioFormat fmt, AudioInputStream is) throws StreamCorruptedException, IOException{
			super(fmt, is);
			Info vi = new Info();
			vd = new DspState();
			pcmx = new float[1][][];
			vb = new Block(vd);
			OggPacket[] hp = OggUtils.getPacketsFromPages(getHeaderPages());
			if(hp.length == 3){
				for(OggPacket p : hp) vi.synthesis_headerin(p);
				vd.synthesis_init(vi);
				index = new int[vi.channels];
				createBuffer(8192);
			}else throw new StreamCorruptedException("Initial packets is missing");
		}

		protected boolean decode(OggPacket packet){
			int samples;
			if(vb.synthesis(packet) == 0) vd.synthesis_blockin(vb);
			while((samples = vd.synthesis_pcmout(pcmx, index)) > 0){
				float[][] pcm = pcmx[0];
				int bout = 4096 / getChannels();
				if(samples < bout) bout = samples;
				byte[] convbuffer = new byte[8192];
				createBuffer(2 * getChannels() * bout);
				for(int i = 0; i < getChannels(); i++){
					int ptr = i * 2, mono = index[i];
					for(int j = 0; j < bout; j++){
						int val = (int) (pcm[i][mono + j] * 32767.);
						if(val > 32767) val = 32767;
						if(val < -32768) val =- 32768;
						if(val < 0) val = val | 0x8000;
						convbuffer[ptr] = (byte) val;
						convbuffer[ptr + 1] = (byte) (val >>> 8);
						ptr += 2 * getChannels();
					}
				}
				for(int c = 0; c < 2 * getChannels() * bout; c++) putInBuffer(convbuffer[c]);
				vd.synthesis_read(bout);
			}
			return true;
		}
	}
}
/*
 * Copyright (c) 2013, Slick2D
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of the Slick2D nor the names of its contributors may be
 *   used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package fluddokt.opsu.fake.openal;

import java.io.IOException;
import java.io.InputStream;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import fluddokt.opsu.fake.*;
//import org.newdawn.slick.util.Log;

/**
 * An input stream that can extract MP3 data.
 *
 * @author fluddokt (https://github.com/fluddokt)
 */
public class Mp3InputStream extends AudioInputStream2 {
	/** The MPEG audio bitstream. */
	private Bitstream bitstream;

	/** The MPEG decoder. */
	private Decoder decoder;

	/** The frame header extractor. */
	private Header header;

	/** The buffer. */
	private SampleBuffer buf;

	/** The number of channels. */
	private int channels;

	/** The sample rate. */
	private int sampleRate;

	/** The buffer length. */
	private int bufLen = 0;

	/** True if we've reached the end of the available data. */
	private boolean endOfStream = false;

	/** The buffer position. */
	private int bpos;

	/**
	 * Create a new stream to decode MP3 data.
	 * @param input the input stream from which to read the MP3 file
	 * @throws IOException 
	 */
	public Mp3InputStream(InputStream input) throws IOException {
		decoder = new Decoder();
		bitstream = new Bitstream(input);
		try {
			header = bitstream.readFrame();
		} catch (BitstreamException e) {
			Log.error(e);
		}

		channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
		sampleRate = header.frequency();
		if(sampleRate == 0){
			System.out.println("MP3 "+sampleRate+" "+channels);
			throw new IOException("Not MP3?");
		}
		buf = new SampleBuffer(sampleRate, channels);
		decoder.setOutputBuffer(buf);

		try {
			decoder.decodeFrame(header, bitstream);
		} catch (DecoderException e) {
			Log.error(e);
		}

		bufLen = buf.getBufferLength();
		//if(bufLen<=0){
		///	Log.error("buf Len is < 0 ");
		//}
		bitstream.closeFrame();
		
		
		while(bufLen<=0){
			try {
				header = bitstream.readFrame();
			} catch (BitstreamException e) {
				Log.error(e);
			}
			try {
				decoder.decodeFrame(header, bitstream);
			} catch (DecoderException e) {
				Log.error(e);
			}
			bufLen = buf.getBufferLength();
			/*if(bufLen<=0){
				Log.error("buf Len is < 0  ");
			}*/
			bitstream.closeFrame();

		}

		
		
	}

	@Override
	public int read() throws IOException {
		if (atEnd())
			return -1;
		while (bpos >= bufLen) {
			if(!nextFrame())
				return -1;
		}

		return buf.getBuffer()[bpos++]&0xffff;
	}

	private boolean nextFrame() {
		try {
			do{
				header = bitstream.readFrame();
				if (header == null) {
					buf.clear_buffer();
					endOfStream = true;
					return false;
				}
				buf.clear_buffer();
				decoder.decodeFrame(header, bitstream);
				bufLen = buf.getBufferLength();
				bitstream.closeFrame();
			}while(bufLen<=0);
		} catch (DecoderException | BitstreamException e) {
			endOfStream = true;
			Log.error(e);
			return false;
		}
		bpos = 0;
		return true;
	}

	@Override
	public boolean atEnd() { return endOfStream; }

	@Override
	public int getChannels() { return channels; }

	@Override
	public int getRate() { return sampleRate; }

	/*@Override
	public int read(short[] b, int off, int len) throws IOException {*/
		/*int i = -1;
		try {
			for (i = 0; i < len; i++) {
				int value = read();
				if (value >= 0)
					b[i] = (short) value;
				else
					return (i == 0) ? -1 : i;
			
			}
		} catch (IOException e) {
			Log.error(e);
			return i;
		}

		return len;*/
	/*	while (bpos >= bufLen) {
			if(!nextFrame())
				return -1;
		}
		if(len > bufLen-bpos)
			len=bufLen-bpos;
		
		System.arraycopy(buf.getBuffer(), bpos, b, off, len);
		bpos+=len;

		return len;
	}*/
	
	public short[] data() {
		return buf.getBuffer();
	}
	
	@Override
	public long skip(long length) throws IOException {
		/*
		 return super.skip(length);
		/*/
		if (bufLen <= 0)
			Log.warn("Mp3InputStream: skip: bufLen not yet determined.");

		int skipped = 0;
		while (skipped + bufLen < length) {
			try {
				header = bitstream.readFrame();
				if (header == null) {
//					Log.warn("Mp3InputStream: skip: header is null.");
					buf.clear_buffer();
					endOfStream = true;
					return -1;
				}

				// last frame that won't be skipped so better read it
				if (skipped + bufLen * 4 >= length || bufLen <= 0) {
					buf.clear_buffer();
					decoder.decodeFrame(header, bitstream);
					bufLen = buf.getBufferLength();
				}
				bitstream.closeFrame();
				skipped += bufLen - bpos;
				bpos = 0;
			} catch (BitstreamException | DecoderException e) {
				Log.error(e);
			}
		}
		if (bufLen - bpos > length - skipped) {
			bpos += length - skipped;
			skipped += length - skipped;
		}
		System.out.println("Mp3Skip: "+skipped+" "+length+" "+bpos);

		return skipped;
		//*/
	}

	@Override
	public void close() throws IOException {
		try {
			bitstream.close();
		} catch (BitstreamException e) {
			e.printStackTrace();
		}
	}
}

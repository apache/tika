/*
 * Licensed to the Apache Software Foundation (ASF) under one or more

 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.hwp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.util.LittleEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HwpStreamReader {
	private Logger log = LoggerFactory.getLogger(getClass());
	private InputStream input;
	private byte[] buf;

	public HwpStreamReader(InputStream inputStream) {
		this.input = inputStream;
		buf = new byte[4];
	}

	/**
	 * 읽을 데이터가 더 있는가?
	 * 
	 * @return
	 * @throws IOException
	 */
	public boolean available() throws IOException {
		return input.available() > 0;
	}

	/**
	 * unsigned 1 byte
	 * 
	 * @return
	 * @throws IOException
	 */
	public short uint8() throws IOException {
		if (ensure(1) == 0)
			return -1;

		return LittleEndian.getUByte(buf);
	}

	/**
	 * unsigned 2 byte
	 * 
	 * @return
	 * @throws IOException
	 */
	public int uint16() throws IOException {
		if (ensure(2) == 0)
			return -1;

		return LittleEndian.getUShort(buf);
	}

	/**
	 * unsigned 2 byte array
	 * 
	 * @param i
	 * @return
	 * @throws IOException
	 */
	public int[] uint16(int i) throws IOException {
		if (i <= 0)
			throw new IllegalArgumentException();

		int[] uints = new int[i];
		for (int ii = 0; ii < i; ii++) {
			if (ensure(2) == 0)
				throw new EOFException();

			uints[ii] = LittleEndian.getUShort(buf);
		}

		return uints;
	}

	/**
	 * unsigned 4 byte
	 * 
	 * @return
	 * @throws IOException
	 */
	public long uint32() throws IOException {
		if (ensure(4) == 0)
			return -1;

		return LittleEndian.getUInt(buf);
	}

	/**
	 * 
	 * @param n
	 * @return
	 * @throws IOException
	 */
	public long skip(long n) throws IOException {
		return input.skip(n);
	}

	/**
	 * n만큼 skip 하지 못할 경우 IOException 을 발생한다
	 * 
	 * @param n
	 * @throws IOException
	 */
	public void ensureSkip(long n) throws IOException {
		long skipped = skip(n);
		if (n != skipped) {
			log.error("Skip failed {} => {}", n, skipped);
			throw new IOException();
		}
	}

	/**
	 * count만큼 바이트를 읽는다. InflaterInputStream의 경우 한번에 count만큼 read가 안되는 경우가 있다.
	 * 그래서 count만큼 읽을 때까지 루프를 실행한다
	 * 
	 * @param count
	 * @return
	 * @throws IOException
	 * @throws EOFException
	 */
	private int ensure(int count) throws IOException, EOFException {
		int total = 0;
		while (total < count) {
			// if (total > 0) {
			// log.warn("한번에 읽기 실패 {}/{}. 다시 읽기 시도함 {}", total, count, input);
			// }

			int read = input.read(buf, total, count - total);
			if (read <= 0)
				break;

			total += read;
		}

		if (total == 0) {
			// end
		} else if (total < count) {
			// unexpected end
			throw new EOFException();
		}

		return total;
	}
}
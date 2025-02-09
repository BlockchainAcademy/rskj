/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.util;

import co.rsk.core.Coin;
import co.rsk.util.HexUtils;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by martin.medina on 3/7/17.
 */
public class HexUtilsTest {


    @Test
    public void test_stringNumberAsBigInt() {

        BigInteger hundred = BigInteger.valueOf(100);

        String hexNumberWithPrefix = "0x64";

        String decNumber = "100";

        BigInteger fromPrefix = HexUtils.stringNumberAsBigInt(hexNumberWithPrefix);

        BigInteger fromDec = HexUtils.stringNumberAsBigInt(decNumber);

        Assert.assertEquals(fromPrefix, hundred);

        Assert.assertEquals(fromDec, hundred);

    }

    @Test
    public void stringToByteArray() {
        Assert.assertArrayEquals(new byte[] { 116, 105, 110, 99, 104, 111 }, HexUtils.stringToByteArray("tincho"));
    }

    @Test
    public void stringHexToByteArrayStartsWithZeroX() {
        Assert.assertArrayEquals(new byte[] { 32 }, HexUtils.stringHexToByteArray("0x20"));
    }

    @Test
    public void stringHexToByteArrayLengthNotModTwo() {
        Assert.assertArrayEquals(new byte[] { 2 }, HexUtils.stringHexToByteArray("0x2"));
    }

    @Test
    public void stringHexToByteArray() {
        Assert.assertArrayEquals(new byte[] { 32 }, HexUtils.stringHexToByteArray("20"));
    }

    @Test
    public void toJsonHex() {
        Assert.assertEquals("0x20", HexUtils.toJsonHex(new byte[] { 32 }));
    }

    @Test
    public void toJsonHexNullInput() {
        Assert.assertEquals("0x00", HexUtils.toJsonHex((byte[])null));
    }

    @Test
    public void toUnformattedJsonHex() {
        Assert.assertEquals("0x20", HexUtils.toUnformattedJsonHex(new byte[] { 0x20 }));
    }

    @Test
    public void toUnformattedJsonHex_nullArray() {
        Assert.assertEquals("0x", HexUtils.toUnformattedJsonHex(null));
    }

    @Test
    public void toUnformattedJsonHex_empty() {
        Assert.assertEquals("0x", HexUtils.toUnformattedJsonHex(new byte[0]));
    }

    @Test
    public void toUnformattedJsonHex_twoHex() {
        Assert.assertEquals("0x02", HexUtils.toUnformattedJsonHex(new byte[] {0x2}));
    }

    @Test
    public void toQuantityJsonHex() {
        byte[] toEncode = new byte[]{0x0A};
        Assert.assertEquals("0xa", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toQuantityJsonHex_Zero() {
        byte[] toEncode = new byte[]{0x00, 0x00};
        Assert.assertEquals("0x0", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void test_JSonHexToLong() {
        String hexNumberWithPrefix = "0x64";

        long value = HexUtils.jsonHexToLong(hexNumberWithPrefix);

        Assert.assertEquals(100L, value);
    }

    @Test(expected = NumberFormatException.class)
    public void test_JSonHexToLong_withWrongParamenter_thenThrowException() {
        HexUtils.jsonHexToLong("64");
    }

    @Test
    public void test_hasHexPrefix() {

        String hexNumberWithPrefix = "0x64";
        String hexNumberWithOutPrefix = "64";

        boolean trueCaseStr = HexUtils.hasHexPrefix(hexNumberWithPrefix);
        boolean falseCaseStr = HexUtils.hasHexPrefix(hexNumberWithOutPrefix);
        boolean falseCaseNull = HexUtils.hasHexPrefix((String)null);

        boolean trueCaseBA = HexUtils.hasHexPrefix(hexNumberWithPrefix.getBytes());
        boolean falseCaseBA = HexUtils.hasHexPrefix(hexNumberWithOutPrefix.getBytes());
        boolean falseCaseBANull = HexUtils.hasHexPrefix((byte[])null);

        Assert.assertTrue(trueCaseStr);
        Assert.assertFalse(falseCaseStr);
        Assert.assertFalse(falseCaseNull);

        Assert.assertTrue(trueCaseBA);
        Assert.assertFalse(falseCaseBA);
        Assert.assertFalse(falseCaseBANull);
    }

    @Test
    public void test_isHexWithPrefix() {

        String hexNumberWithPrefix = "0x64";
        String hexNumberWithOutPrefix = "64";
        String hexTooShort = "a";

        boolean trueCase = HexUtils.isHexWithPrefix(hexNumberWithPrefix);
        boolean falseCase = HexUtils.isHexWithPrefix(hexNumberWithOutPrefix);
        boolean falseCaseShort = HexUtils.isHexWithPrefix(hexTooShort);
        boolean nullCase = HexUtils.isHexWithPrefix(null);

        Assert.assertTrue(trueCase);
        Assert.assertFalse(falseCase);
        Assert.assertFalse(falseCaseShort);
        Assert.assertFalse(nullCase);
    }

    @Test
    public void toQuantityJsonHex_EmptyByteArray() {
        byte[] toEncode = new byte[0];
        Assert.assertEquals("0x0", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void test_isHex() {
        String hex = "64ffde45";
        String notHex = "w64ffde45";

        Assert.assertTrue(HexUtils.isHex(hex));
        Assert.assertFalse(HexUtils.isHex(notHex));
        Assert.assertFalse(HexUtils.isHex(null));
    }
    
    @Test
    public void toJsonHexCoin() {
        Assert.assertEquals("1234", HexUtils.toJsonHex(new Coin(new BigInteger("1234"))));
    }

    @Test
    public void toJsonHexNullCoin() {
        Assert.assertEquals("", HexUtils.toJsonHex((Coin) null));
    }

    @Test
    public void stringHexToBigIntegerDefaultCase() {
        Assert.assertEquals(new BigInteger("1"), HexUtils.stringHexToBigInteger("0x1"));
    }

    @Test
    public void stringHexToBigIntegerDefaultCase2() {
        Assert.assertEquals(new BigInteger("255"), HexUtils.stringHexToBigInteger("0xff"));
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenThereIsNoNumber() {
        HexUtils.stringHexToBigInteger("0x");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsNotHexa() {
        HexUtils.stringHexToBigInteger("0xg");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItHasLessThanTwoCharacters() {
        HexUtils.stringHexToBigInteger("0");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsEmpty() {
        HexUtils.stringHexToBigInteger("");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItDoesNotStartWith0x() {
        HexUtils.stringHexToBigInteger("0d99");
    }

    @Test(expected = RskJsonRpcRequestException.class)
    public void test_encodeToHexByteArray_whenNullProvided_expectException() {
        HexUtils.encodeToHexByteArray(null);
    }
    
    @Test
    public void test_encodeToHexByteArray_compare_preencoded() {

        byte[] strBytes = "internet".getBytes();

        String encoded = "0x" + Hex.toHexString(strBytes);

        byte[] strEncoded = HexUtils.encodeToHexByteArray(strBytes);

        Assert.assertTrue(Arrays.equals(encoded.getBytes(), strEncoded));
    }

    @Test
    public void test_decode() {
        Assert.assertEquals(17, ByteBuffer.wrap(HexUtils.decode("11".getBytes())).get());
    }
    
    @Test
    public void test_removeHexPrefix() {

        byte[] data = "0x746573746530".getBytes();

        byte[] clean = HexUtils.removeHexPrefix(data);

        Assert.assertEquals("746573746530", new String(clean));
    }

    @Test
    public void test_leftPad() {

        String hex = "a";

        byte[] res = HexUtils.leftPad(hex.getBytes());

        Assert.assertEquals("0a", new String(res));

    }
    
    @Test
    public void test_jsonHexToInt() {
        Assert.assertEquals(4095, HexUtils.jsonHexToInt("0xfff"));
    }

}

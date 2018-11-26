/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/* JOrbis
 * Copyright (C) 2000 ymnk, JCraft,Inc.
 *  
 * Written by: 2000 ymnk<ymnk@jcraft.com>
 * Modified by: 2018 Allan Taborda dos Santos <allan-taborda@bol.com.br>
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

class CodeBook{
	private int dim; // codebook dimensions (elements per vector)
	private int entries; // codebook entries
	private StaticCodeBook c = new StaticCodeBook();
	private float[] valuelist; // list of dim * entries actual entry values
	private DecodeAux decode_tree;
	private int[] t = new int[15]; // decodevs_add is synchronized for re-using t.

	int getCodebookDimensions(){
		return dim;
	}

	synchronized int decodevs_add(float[] a, int offset, Buffer b, int n){
		int step = n / dim;
		int entry;
		int i, j, o;
		if(t.length < step) t = new int[step];
		for(i = 0; i < step; i++){
			entry = decode(b);
			if(entry == -1) return -1;
			t[i] = entry * dim;
		}
		for(i = 0, o = 0; i < dim; i++, o += step){
			for(j = 0; j < step; j++){
				a[offset + o + j] += valuelist[t[j] + i];
			}
		}
		return 0;
	}

	int decodev_add(float[] a, int offset, Buffer b, int n){
		int i, j, entry, t;
		if(dim > 8){
			for(i = 0; i < n;){
				entry = decode(b);
				if(entry == -1) return -1;
				t = entry * dim;
				for(j = 0; j < dim;) a[offset + (i++)] += valuelist[t + (j++)];
			}
		}else{
			for(i = 0; i < n;){
				entry = decode(b);
				if(entry == -1) return -1;
				t = entry * dim;
				j = 0;
				switch(dim){
					case 8:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 7:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 6:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 5:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 4:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 3:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 2:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 1:
						a[offset + (i++)] += valuelist[t + (j++)];
					case 0:
						break;
				}
			}
		}
		return 0;
	}

	int decodev_set(float[] a, int offset, Buffer b, int n){
		int i, j, entry, t;
		for(i = 0; i < n;){
			entry = decode(b);
			if(entry == -1) return -1;
			t = entry * dim;
			for(j = 0; j < dim;) a[offset + i++] = valuelist[t + (j++)];
		}
		return 0;
	}

	int decodevv_add(float[][] a, int offset, int ch, Buffer b, int n){
		int i, j, entry, chptr = 0;
		for(i = offset / ch; i < (offset + n) / ch;){
			entry = decode(b);
			if(entry == -1) return -1;
			int t = entry * dim;
			for(j = 0; j < dim; j++){
				a[chptr++][i] += valuelist[t + j];
				if(chptr == ch){
					chptr = 0;
					i++;
				}
			}
		}
		return 0;
	}

	// Decode side is specced and easier, because we don't need to find matches using different criteria; we simply read and map. There are
	// two things we need to do 'depending': We may need to support interleave.  We don't really, but it's convenient to do it here rather
	// than rebuild the vector later. Cascades may be additive or multiplicitive; this is not inherent in the codebook, but set in the code
	// using the codebook. Like interleaving, it's easiest to do it here.  
	// stage==0 -> declarative (set the value)
	// stage==1 -> additive
	// stage==2 -> multiplicitive
	// returns the entry number or -1 on eof
	int decode(Buffer b){
		int ptr = 0;
		DecodeAux t = decode_tree;
		int lok = b.look(t.tabn);
		if(lok >= 0){
			ptr=t.tab[lok];
			b.adv(t.tabl[lok]);
			if(ptr <= 0) return -ptr;
		}
		do{
			switch(b.read1()){
			case 0:
				ptr = t.ptr0[ptr];
				break;
			case 1:
				ptr = t.ptr1[ptr];
				break;
			case -1:
			default:
				return -1;
			}
		}while(ptr > 0);
		return -ptr;
	}

	int init_decode(StaticCodeBook s){
		c = s;
		entries = s.entries;
		dim = s.dim;
		valuelist = s.unquantize();
		decode_tree = make_decode_tree();
		return decode_tree == null ? -1 : 0;
	}

	// given a list of word lengths, generate a list of codewords. Works for length ordered or unordered,
	// always assigns the lowest valued codewords first. Extended to handle unused entries (length 0)
	static int[] make_words(int[] l, int n){
		int[] marker = new int[33];
		int[] r = new int[n];
		for(int i = 0; i < n; i++) {
			int length = l[i];
			if (length > 0){
				int entry = marker[length];
				// when we claim a node for an entry, we also claim the nodes below it (pruning off the imagined tree that
				// may have dangledfrom it) as well as blocking the use of any nodes directly above for leaves update ourself
				if(length < 32 && (entry >>> length) != 0){
					// error condition; the lengths must specify an overpopulated tree free(r);
					return null;
				}
				r[i] = entry;
				// Look to see if the next shorter marker points to the node above. if so, update it and repeat.
				for(int j = length; j > 0; j--){
					if((marker[j] & 1) != 0) {
						// have to jump branches
						if(j == 1) marker[1]++;
						else marker[j] = marker[j - 1] << 1;
						break; // invariant says next upper marker would already have been moved if it was on the same path
					}
					marker[j]++;
				}
				// prune the tree; the implicit invariant says all the longer markers were dangling from
				// our just-taken node. Dangle them from our *new* node.
				for(int j = length + 1; j < 33; j++){
					if((marker[j] >>> 1) == entry){
						entry = marker[j];
						marker[j] = marker[j - 1] << 1;
					}else{
						break;
					}
				}
			}
		}
		// bitreverse the words because our bitwise packer/unpacker is LSb endian
		for(int i = 0; i < n; i++){
			int temp = 0;
			for(int j = 0; j < l[i]; j++){
				temp <<= 1;
				temp |= (r[i] >>> j) & 1;
			}
			r[i] = temp;
		}
		return r;
	}

	// build the decode helper tree from the codewords
	DecodeAux make_decode_tree(){
		int top = 0;
		DecodeAux t = new DecodeAux();
		int[] ptr0 = t.ptr0 = new int[entries * 2];
		int[] ptr1 = t.ptr1 = new int[entries * 2];
		int[] codelist = make_words(c.lengthlist, c.entries);
		if(codelist == null) return null;
		t.aux = entries * 2;
		for(int i = 0; i<entries; i++){
			if(c.lengthlist[i]>0){
				int ptr = 0, j;
				for(j = 0; j < c.lengthlist[i] - 1; j++){
					int bit = (codelist[i] >>> j) & 1;
					if(bit == 0){
						if(ptr0[ptr] == 0) ptr0[ptr]=++top;
						ptr=ptr0[ptr];
					}else{
						if(ptr1[ptr] == 0) ptr1[ptr] = ++top;
						ptr=ptr1[ptr];
					}
				}
				if(((codelist[i] >>> j) & 1) == 0) ptr0[ptr] = -i;
				else ptr1[ptr]=-i;
			}
		}
		t.tabn = Util.ilog(entries) - 4;
		if(t.tabn < 5) t.tabn = 5;
		int n = 1 << t.tabn;
		t.tab = new int[n];
		t.tabl = new int[n];
		for(int i = 0; i < n; i++){
			int p = 0, j = 0;
			for(j = 0; j < t.tabn && (p > 0 || j == 0); j++){
				if((i & (1 << j)) != 0) p = ptr1[p];
				else p = ptr0[p];
			}
			t.tab[i] = p; // -code
			t.tabl[i] = j; // length 
		}
		return t;
	}

	class DecodeAux{
		int[] tab;
		int[] tabl;
		int tabn;
		int[] ptr0;
		int[] ptr1;
		int aux; // number of tree entries
	}
}
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

class Residue2 extends Residue0{
	private static int[][] _2inverse_partword;

	int inverse(Block vb, Object vl, float[][] in, int[] nonzero, int ch){
		int i = 0;
		for(i = 0; i < ch; i++) if(nonzero[i] != 0) break;
		if(i == ch) return 0; /* no nonzero vectors */
		return _2inverse(vb, vl, in, ch);
	}

	synchronized static int _2inverse(Block vb, Object vl, float[][] in, int ch) {
		int i, k, l, s;
		LookResidue0 look = (LookResidue0) vl;
		InfoResidue0 info = look.info;
		// move all this setup out later
		int samples_per_partition = info.grouping;
		int partitions_per_word = look.phrasebook.getCodebookDimensions();
		int n = info.end - info.begin;
		int partvals = n / samples_per_partition;
		int partwords = (partvals + partitions_per_word - 1) / partitions_per_word;
		if(_2inverse_partword == null || _2inverse_partword.length < partwords) _2inverse_partword = new int[partwords][];
		for(s = 0; s < look.stages; s++){
			for(i = 0, l = 0; i < partvals; l++){
				if(s == 0){
					// fetch the partition word for each channel
					int temp = look.phrasebook.decode(vb.opb);
					if(temp == -1) return 0;
					_2inverse_partword[l] = look.decodemap[temp];
					if(_2inverse_partword[l] == null) return 0;
				}
				// now we decode residual values for the partitions
				for (k = 0; k < partitions_per_word && i < partvals; k++, i++) {
					int offset = info.begin + i * samples_per_partition;
					int index = _2inverse_partword[l][k];
					if((info.secondstages[index] & (1 << s)) != 0){
						CodeBook stagebook = look.fullbooks[look.partbooks[index][s]];
						if(stagebook != null && stagebook.decodevv_add(in, offset, ch, vb.opb, samples_per_partition) == -1) return 0;
					}
				}
			}
		}
		return 0;
	}
}
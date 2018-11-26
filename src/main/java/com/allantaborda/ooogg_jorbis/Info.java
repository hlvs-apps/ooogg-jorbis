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

import com.allantaborda.ooogg.OggPacket;

class Info{
	private static final int VI_TIMEB = 1;
	private static final int VI_FLOORB = 2;
	private static final int VI_RESB = 3;
	private static final int VI_MAPB = 1;
	private static final int VI_WINDOWB = 1;
	private int version;
	int channels;
	private int rate;
	int[] blocksizes = new int[2];
	int modes;
	private int maps;
	int times;
	int floors;
	int residues;
	int books;
	InfoMode[] mode_param;
	int[] map_type;
	Object[] map_param;
	private int[] time_type;
	int[] floor_type;
	Object[] floor_param;
	int[] residue_type;
	Object[] residue_param;
	StaticCodeBook[] book_param;

	public void clear(){
		for(int i = 0; i < modes; i++) mode_param[i] = null;
		mode_param = null;
		map_param = null;
		floor_param = null;
		residue_param = null;
		// the static codebooks *are* freed if you call info_clear, because
		// decode side does alloc a 'static' codebook. Calling clear on the
		// full codebook does not clear the static codebook (that's our
		// responsibility)
		for(int i = 0; i < books; i++){
			// just in case the decoder pre-cleared to save space
			if(book_param[i] != null) book_param[i] = null;
		}
		//if(vi->book_param)free(vi->book_param);
		book_param = null;
	}

	int unpack_info(Buffer opb){
		version = opb.read(32);
		if(version != 0) return -1;
		channels = opb.read(8);
		rate = opb.read(32);
		opb.read(32); //bitrate_upper
		opb.read(32); //bitrate_nominal
		opb.read(32); //bitrate_lower
		blocksizes[0] = 1 << opb.read(4);
		blocksizes[1] = 1 << opb.read(4);
		if(rate < 1 || channels < 1 || blocksizes[0] < 8 || blocksizes[1] < blocksizes[0] || opb.read(1) != 1){
			clear();
			return -1;
		}
		return 0;
	}

	int unpack_books(Buffer opb){
		books = opb.read(8) + 1;
		if(book_param == null || book_param.length != books) book_param = new StaticCodeBook[books];
		for(int i = 0; i < books; i++){
			book_param[i] = new StaticCodeBook();
			if(book_param[i].unpack(opb) != 0){
				clear();
				return -1;
			}
		}
		// time backend settings
		times = opb.read(6) + 1;
		if(time_type == null || time_type.length != times) time_type = new int[times];
		for(int i = 0; i < times; i++){
			time_type[i] = opb.read(16);
			if(time_type[i] < 0 || time_type[i] >= VI_TIMEB){
				clear();
				return -1;
			}
		}
		// floor backend settings
		floors = opb.read(6) + 1;
		if(floor_type == null || floor_type.length != floors) floor_type = new int[floors];
		if(floor_param == null || floor_param.length != floors) floor_param = new Object[floors];
		for(int i = 0; i < floors; i++){
			floor_type[i]=opb.read(16);
			if(floor_type[i] < 0 || floor_type[i] >= VI_FLOORB){
				clear();
				return -1;
			}
			floor_param[i] = Floor.floor_P[floor_type[i]].unpack(this, opb);
			if(floor_param[i] == null){
				clear();
				return -1;
			}
		}
		// residue backend settings
		residues = opb.read(6) + 1;
		if(residue_type == null || residue_type.length != residues) residue_type = new int[residues];
		if(residue_param == null || residue_param.length != residues) residue_param = new Object[residues];
		for(int i=0; i < residues; i++){
			residue_type[i] = opb.read(16);
			if(residue_type[i] < 0 || residue_type[i] >= VI_RESB){
				clear();
				return -1;
			}
			residue_param[i] = Residue0.residue_P[residue_type[i]].unpack(this, opb);
			if(residue_param[i] == null){
				clear();
				return -1;
			}
		}
		// map backend settings
		maps = opb.read(6) + 1;
		if(map_type == null || map_type.length != maps)
			map_type=new int[maps];
		if(map_param==null || map_param.length != maps)
			map_param=new Object[maps];
		for(int i = 0; i < maps; i++){
			map_type[i] = opb.read(16);
			if(map_type[i] < 0 || map_type[i] >= VI_MAPB){
				clear();
				return -1;
			}
			map_param[i] = Mapping0.mapping_P[map_type[i]].unpack(this, opb);
			if(map_param[i] == null){
				clear();
				return -1;
			}
		}
		// mode settings
		modes = opb.read(6) + 1;
		if(mode_param == null || mode_param.length != modes)
			mode_param = new InfoMode[modes];
		for(int i = 0; i < modes; i++){
			mode_param[i] = new InfoMode();
			mode_param[i].blockflag = opb.read(1);
			mode_param[i].windowtype = opb.read(16);
			mode_param[i].transformtype = opb.read(16);
			mode_param[i].mapping = opb.read(8);
			if((mode_param[i].windowtype >= VI_WINDOWB)
					|| (mode_param[i].transformtype >= VI_WINDOWB)
					|| (mode_param[i].mapping >= maps)){
				clear();
				return -1;
			}
		}
		if(opb.read(1) != 1){
			clear();
			return -1;
		}
		return 0;
	}

	public int synthesis_headerin(OggPacket op){
		Buffer opb = new Buffer();
		if(op != null){
			opb.readinit(op.getContent(), 0, op.getSize());
			// Which of the three types of header is this?
			// Also verify header-ness, vorbis
			byte[] buffer = new byte[6];
			int packtype = opb.read(8);
			opb.read(buffer, 6);
			if(buffer[0] != 'v' || buffer[1] != 'o' || buffer[2] != 'r' || buffer[3] != 'b' || buffer[4] != 'i' || buffer[5] != 's'){
				// not a vorbis header
				return -1;
			}
			switch(packtype){
			case 0x01: // least significant *bit* is read first
				//if(op.b_o_s==0){
				// Not the initial packet
				//return (-1);
				//}
				if(rate != 0){
					// previously initialized info header
					return -1;
				}
				return unpack_info(opb);
			case 0x03: // least significant *bit* is read first
				if(rate == 0){
					// um... we didn't get the initial header
					return -1;
				}
				return 0;
			case 0x05: // least significant *bit* is read first
				if(rate == 0){
					// um... we didn;t get the initial header or comments yet
					return -1;
				}
				return unpack_books(opb);
			default:
				// Not a valid vorbis header type
				//return(-1);
				break;
			}
		}
		return -1;
	}
}
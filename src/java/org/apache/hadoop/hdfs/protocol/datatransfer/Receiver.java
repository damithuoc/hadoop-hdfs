/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.protocol.datatransfer;

import static org.apache.hadoop.hdfs.protocol.HdfsProtoUtil.fromProto;
import static org.apache.hadoop.hdfs.protocol.HdfsProtoUtil.fromProtos;
import static org.apache.hadoop.hdfs.protocol.HdfsProtoUtil.vintPrefixed;
import static org.apache.hadoop.hdfs.protocol.datatransfer.DataTransferProtoUtil.fromProto;

import java.io.DataInputStream;
import java.io.IOException;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpBlockChecksumProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpCopyBlockProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpReadBlockProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpReplaceBlockProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpTransferBlockProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpWriteBlockProto;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.security.token.Token;

/** Receiver */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public abstract class Receiver {
  /** Read an Op.  It also checks protocol version. */
  protected final Op readOp(DataInputStream in) throws IOException {
    final short version = in.readShort();
    if (version != DataTransferProtocol.DATA_TRANSFER_VERSION) {
      throw new IOException( "Version Mismatch (Expected: " +
          DataTransferProtocol.DATA_TRANSFER_VERSION  +
          ", Received: " +  version + " )");
    }
    return Op.read(in);
  }

  /** Process op by the corresponding method. */
  protected final void processOp(Op op, DataInputStream in
      ) throws IOException {
    switch(op) {
    case READ_BLOCK:
      opReadBlock(in);
      break;
    case WRITE_BLOCK:
      opWriteBlock(in);
      break;
    case REPLACE_BLOCK:
      opReplaceBlock(in);
      break;
    case COPY_BLOCK:
      opCopyBlock(in);
      break;
    case BLOCK_CHECKSUM:
      opBlockChecksum(in);
      break;
    case TRANSFER_BLOCK:
      opTransferBlock(in);
      break;
    default:
      throw new IOException("Unknown op " + op + " in data stream");
    }
  }

  /** Receive OP_READ_BLOCK */
  private void opReadBlock(DataInputStream in) throws IOException {
    OpReadBlockProto proto = OpReadBlockProto.parseFrom(vintPrefixed(in));
    
    ExtendedBlock b = fromProto(
        proto.getHeader().getBaseHeader().getBlock());
    Token<BlockTokenIdentifier> token = fromProto(
        proto.getHeader().getBaseHeader().getToken());

    opReadBlock(in, b, proto.getOffset(), proto.getLen(),
        proto.getHeader().getClientName(), token);
  }
  /**
   * Abstract OP_READ_BLOCK method. Read a block.
   */
  protected abstract void opReadBlock(DataInputStream in, ExtendedBlock blk,
      long offset, long length, String client,
      Token<BlockTokenIdentifier> blockToken) throws IOException;
  
  /** Receive OP_WRITE_BLOCK */
  private void opWriteBlock(DataInputStream in) throws IOException {
    final OpWriteBlockProto proto = OpWriteBlockProto.parseFrom(vintPrefixed(in));
    opWriteBlock(in,
        fromProto(proto.getHeader().getBaseHeader().getBlock()),
        proto.getPipelineSize(),
        fromProto(proto.getStage()),
        proto.getLatestGenerationStamp(),
        proto.getMinBytesRcvd(), proto.getMaxBytesRcvd(),
        proto.getHeader().getClientName(),
        fromProto(proto.getSource()),
        fromProtos(proto.getTargetsList()),
        fromProto(proto.getHeader().getBaseHeader().getToken()));
  }

  /**
   * Abstract OP_WRITE_BLOCK method. 
   * Write a block.
   */
  protected abstract void opWriteBlock(DataInputStream in, ExtendedBlock blk,
      int pipelineSize, BlockConstructionStage stage, long newGs,
      long minBytesRcvd, long maxBytesRcvd, String client, DatanodeInfo src,
      DatanodeInfo[] targets, Token<BlockTokenIdentifier> blockToken)
      throws IOException;

  /** Receive {@link Op#TRANSFER_BLOCK} */
  private void opTransferBlock(DataInputStream in) throws IOException {
    final OpTransferBlockProto proto =
      OpTransferBlockProto.parseFrom(vintPrefixed(in));

    opTransferBlock(in,
        fromProto(proto.getHeader().getBaseHeader().getBlock()),
        proto.getHeader().getClientName(),
        fromProtos(proto.getTargetsList()),
        fromProto(proto.getHeader().getBaseHeader().getToken()));
  }

  /**
   * Abstract {@link Op#TRANSFER_BLOCK} method.
   * For {@link BlockConstructionStage#TRANSFER_RBW}
   * or {@link BlockConstructionStage#TRANSFER_FINALIZED}.
   */
  protected abstract void opTransferBlock(DataInputStream in, ExtendedBlock blk,
      String client, DatanodeInfo[] targets,
      Token<BlockTokenIdentifier> blockToken)
      throws IOException;

  /** Receive OP_REPLACE_BLOCK */
  private void opReplaceBlock(DataInputStream in) throws IOException {
    OpReplaceBlockProto proto = OpReplaceBlockProto.parseFrom(vintPrefixed(in));

    opReplaceBlock(in,
        fromProto(proto.getHeader().getBlock()),
        proto.getDelHint(),
        fromProto(proto.getSource()),
        fromProto(proto.getHeader().getToken()));
  }

  /**
   * Abstract OP_REPLACE_BLOCK method.
   * It is used for balancing purpose; send to a destination
   */
  protected abstract void opReplaceBlock(DataInputStream in,
      ExtendedBlock blk, String delHint, DatanodeInfo src,
      Token<BlockTokenIdentifier> blockToken) throws IOException;

  /** Receive OP_COPY_BLOCK */
  private void opCopyBlock(DataInputStream in) throws IOException {
    OpCopyBlockProto proto = OpCopyBlockProto.parseFrom(vintPrefixed(in));
    
    opCopyBlock(in,
        fromProto(proto.getHeader().getBlock()),
        fromProto(proto.getHeader().getToken()));
  }

  /**
   * Abstract OP_COPY_BLOCK method. It is used for balancing purpose; send to
   * a proxy source.
   */
  protected abstract void opCopyBlock(DataInputStream in, ExtendedBlock blk,
      Token<BlockTokenIdentifier> blockToken)
      throws IOException;

  /** Receive OP_BLOCK_CHECKSUM */
  private void opBlockChecksum(DataInputStream in) throws IOException {
    OpBlockChecksumProto proto = OpBlockChecksumProto.parseFrom(vintPrefixed(in));
    
    opBlockChecksum(in,
        fromProto(proto.getHeader().getBlock()),
        fromProto(proto.getHeader().getToken()));
  }

  /**
   * Abstract OP_BLOCK_CHECKSUM method.
   * Get the checksum of a block 
   */
  protected abstract void opBlockChecksum(DataInputStream in,
      ExtendedBlock blk, Token<BlockTokenIdentifier> blockToken)
      throws IOException;
}

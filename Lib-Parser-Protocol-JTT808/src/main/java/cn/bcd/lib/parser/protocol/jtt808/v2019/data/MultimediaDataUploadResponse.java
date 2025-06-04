package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import io.netty.buffer.ByteBuf;

public class MultimediaDataUploadResponse implements PacketBody {
    //多媒体数据id
    public long id;
    //重传包总数
    public short num;
    //重传包id列表
    public byte[] data;

    public static MultimediaDataUploadResponse read(ByteBuf data, int len) {
        MultimediaDataUploadResponse multiMediaDataUploadResponse = new MultimediaDataUploadResponse();
        multiMediaDataUploadResponse.id = data.readUnsignedInt();
        multiMediaDataUploadResponse.num = data.readUnsignedByte();
        byte[] bytes = new byte[len - 5];
        data.readBytes(bytes);
        multiMediaDataUploadResponse.data = bytes;
        return multiMediaDataUploadResponse;
    }

    public void write(ByteBuf data){
        data.writeInt((int) id);
        data.writeByte(num);
        data.writeBytes(this.data);
    }
}

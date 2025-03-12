- 上传文件

  path参数可以不传

  curl -X POST http://10.0.11.50:24680/upload -F "file=@text.txt" -F "path=test.txt"

- 下载文件
- wget http://10.0.11.50:24680/download?path=test.txt

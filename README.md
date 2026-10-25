# Custom TTS Dictionary

Ứng dụng Android viết bằng ngôn ngữ Java thuần, đóng vai trò là một dịch vụ chuyển văn bản thành giọng nói (Text-To-Speech - TTS) trung gian trong hệ sinh thái Android. Ứng dụng cho phép người dùng tùy chỉnh cách phát âm của các từ hoặc cụm từ theo quy tắc riêng mà không làm chậm tốc độ phản hồi của công cụ giọng đọc gốc trên máy (như Google TTS, Vocalizer, Samsung TTS hay các bản sao chép Google TTS khác gói).

- Tên ứng dụng hiển thị: Custom TTS Dictionary
- Tên gói (Package Name): com.smartthinh.customttsdictionary
- Cơ chế lưu trữ: Toàn bộ từ điển được lưu trong vùng nhớ riêng tư nội bộ của ứng dụng, hoạt động độc lập và không yêu cầu cấp các quyền truy cập bộ nhớ nguy hiểm.

---

## Các ưu điểm nổi bật

1. Tốc độ phản hồi tức thì:
   Ứng dụng sử dụng cơ chế xử lý luồng dữ liệu đệm nối tiếp (Pipelined Streaming). Khi nhận văn bản cần đọc, câu đầu tiên được xử lý và phát ra loa chỉ sau khoảng 20 đến 30 mili-giây, các câu tiếp theo được tổng hợp ngầm nối tiếp. Văn bản ngắn hay dài đều phát ra tiếng ngay lập tức.

2. Khả năng xử lý từ điển lớn chống treo máy:
   Dữ liệu từ điển được nạp và tìm kiếm bằng các thuật toán tối ưu trên luồng riêng biệt, có thanh tiến trình và nút hủy khi mở từ điển dung lượng lớn, giúp tránh hoàn toàn tình trạng giật lag hay treo ứng dụng (ANR) kể cả khi từ điển có hàng triệu từ.

3. Tương thích toàn diện:
   Hỗ trợ đọc chuẩn xác với mọi loại công cụ TTS đã cài trên thiết bị, bao gồm các bản mod Google TTS mang tên gói riêng biệt mà không bị hệ thống ép quay về công cụ mặc định.

---

## Hướng dẫn thiết lập ban đầu

Sau khi cài đặt tệp APK vào điện thoại, bạn thực hiện 2 bước đơn giản sau:

1. Chọn công cụ phát âm nền:
   Mở ứng dụng, nhấn vào nút "Chọn công cụ TTS" ở thanh trên cùng. Một danh sách các công cụ TTS đang có trên điện thoại sẽ hiện ra, bạn chỉ cần chạm vào công cụ muốn dùng để phát âm (ví dụ: Google TTS, Vocalizer,...).

2. Kích hoạt dịch vụ trong hệ điều hành Android:
   Nhấn vào nút "Cài đặt TTS hệ thống" ở thanh trên cùng để mở nhanh phần cài đặt đầu ra Chuyển văn bản thành giọng nói của máy. Tại mục "Công cụ ưa thích", bạn chọn "Custom TTS Dictionary".

---

## Hướng dẫn sử dụng các chức năng

Toàn bộ các nút chức năng chính đều được sắp xếp trên một hàng ngang ở cạnh trên màn hình: Thêm từ điển, Nhập từ điển, Chọn công cụ TTS, Cài đặt TTS hệ thống, Truy cập trang web.

### 1. Cách tạo một bộ từ điển mới

- Bước 1: Nhấn vào nút "Thêm từ điển" trên thanh công cụ ở đầu màn hình.
- Bước 2: Một hộp thoại hiện ra, bạn nhập tên bộ từ điển mong muốn vào ô nhập liệu (ví dụ: TuDienChung, DocTruyen,...).
- Bước 3: Nhấn nút OK để lưu. Bộ từ điển mới sẽ xuất hiện ngay trong danh sách chính.

### 2. Cách kích hoạt bộ từ điển để sử dụng

- Trong danh sách các bộ từ điển ở màn hình chính, bạn chạm vào bộ từ điển muốn áp dụng.
- Bộ từ điển đó sẽ hiển thị trạng thái "Đang sử dụng".
- Dịch vụ TTS sẽ lập tức nạp các quy tắc của bộ từ điển này vào bộ nhớ để thay thế cách phát âm khi có văn bản gửi tới.

### 3. Cách nhập bộ từ điển có sẵn (tệp định dạng .dic)

Nếu bạn đã có sẵn các tệp từ điển (ví dụ các tệp định dạng .dic đã được chuẩn bị từ trước):

- Bước 1: Nhấn vào nút "Nhập từ điển" trên thanh công cụ ở đầu màn hình.
- Bước 2: Trình chọn tệp của hệ thống Android sẽ mở ra. Bạn điều hướng đến thư mục chứa tệp và chọn tệp từ điển cần dùng (chỉ chấp nhận tệp có phần đuôi mở rộng là .dic).
- Bước 3: Ứng dụng sẽ tự động sao chép an toàn tệp vào vùng nhớ riêng bằng luồng đệm tốc độ cao mà không làm đơ máy. Sau khi nhập xong, bộ từ điển sẽ xuất hiện trong danh sách với tên chính là tên của tệp .dic bạn vừa chọn.

### 4. Quản lý và chỉnh sửa các từ trong bộ từ điển

Để mở danh sách từ của một bộ từ điển:
- Bạn chạm vào bộ từ điển đang có trạng thái "Đang sử dụng".
- Một hộp thoại lớn sẽ mở ra hiển thị toàn bộ các từ có trong bộ từ điển đó.

Các thao tác bên trong hộp thoại này gồm:

- Tìm kiếm từ nhanh theo thời gian thực:
  Ô "Tìm kiếm từ..." nằm ngay trên đầu danh sách. Bạn chỉ cần gõ ký tự vào ô này, danh sách bên dưới sẽ lập tức lọc và hiển thị các kết quả phù hợp mà không phân biệt chữ hoa hay chữ thường. Có thể tìm theo cả từ gốc hoặc từ thay thế.

- Thêm từ mới:
  Nhấn vào nút "Thêm từ" ở góc dưới bên trái màn hình. Nhập từ gốc (từ không nên đọc) vào ô thứ nhất, nhập từ thay thế (từ nên đọc) vào ô thứ hai, sau đó nhấn OK để lưu.

- Chỉnh sửa từ:
  Chạm trực tiếp vào một từ bất kỳ trong danh sách. Hộp thoại chỉnh sửa sẽ mở ra với hai ô nhập liệu đã được điền sẵn nội dung cũ. Bạn sửa lại theo ý muốn rồi nhấn OK để lưu thay đổi.

- Xóa một từ:
  Nhấn và giữ (chạm lâu) vào từ bạn muốn xóa trong danh sách. Một hộp thoại xác nhận sẽ hỏi bạn có chắc chắn muốn xóa từ đó không. Nhấn OK để xóa từ.

- Xóa toàn bộ từ:
  Nhấn vào nút "Xóa tất cả từ" ở hàng nút phía dưới đáy màn hình. Xác nhận OK để xóa sạch toàn bộ từ vựng trong bộ từ điển hiện tại.

- Xuất từ điển ra bộ nhớ điện thoại:
  Nhấn vào nút "Xuất từ điển" ở hàng nút phía dưới đáy màn hình. Ứng dụng sẽ xuất tệp .dic ra thư mục Downloads (Tải về) của máy với tên tệp trùng với tên bộ từ điển của bạn.

---

## Cấu trúc định dạng tệp từ điển (.dic)

Tệp từ điển sử dụng định dạng văn bản thuần túy chuẩn UTF-8:
- Mỗi quy tắc từ điển nằm trên một dòng riêng biệt.
- Từ gốc và từ thay thế được phân tách nhau bằng dấu phân cách (dấu tab hoặc dấu hai chấm).
- Ví dụ:
  tu_goc	tu_thay_the
  hello	xin chao

---

## Hướng dẫn tự biên dịch mã nguồn thành tệp APK

Dự án đã được tích hợp sẵn kịch bản biên dịch độc lập, không phụ thuộc vào Gradle nặng nề:

1. Cách thuận tiện nhất trên máy tính Windows:
   Nhấp đúp chuột trực tiếp vào tệp:
   build_apk.bat

2. Cách sử dụng dòng lệnh:
   Mở Command Prompt hoặc PowerShell tại thư mục dự án và chạy:
   python build_apk.py

Toàn bộ quy trình từ kiểm tra Android SDK, biên dịch tài nguyên XML, dịch mã nguồn Java, đóng gói DEX, căn lề và ký số APK đều được tự động hóa 100%. Tệp APK tạo thành sẽ xuất hiện tại thư mục gốc với tên mang theo tên ứng dụng và số phiên bản, ví dụ:
Custom_TTS_Dictionary_v1.0.apk

---

## Giấy phép (License)

Dự án này được phát hành theo giấy phép mã nguồn mở GNU General Public License v3.0 (GPL-3.0). Vui lòng xem tệp LICENSE để biết thêm chi tiết.

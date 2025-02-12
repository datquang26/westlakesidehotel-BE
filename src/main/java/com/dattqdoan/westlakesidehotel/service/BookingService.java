package com.dattqdoan.westlakesidehotel.service;

import com.dattqdoan.westlakesidehotel.exception.InvalidBookingRequestException;
import com.dattqdoan.westlakesidehotel.exception.ResourceNotFoundException;
import com.dattqdoan.westlakesidehotel.model.BookedRoom;
import com.dattqdoan.westlakesidehotel.model.Room;
import com.dattqdoan.westlakesidehotel.repository.BookingRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    @Resource
    private BookingRepository bookingRepository;
    @Resource
    private RoomService roomService;
    public List<BookedRoom> getAllBookingsByRoomId(Long roomId) {
        return bookingRepository.findByRoomId(roomId);
    }

    public List<BookedRoom> getAllBookings() {
        return bookingRepository.findAll();
    }

    public List<BookedRoom> getBookingsByUserEmail(String email) {
        return bookingRepository.findByGuestEmail(email);
    }

    public void cancelBooking(Long bookingId) {
        bookingRepository.deleteById(bookingId);
    }

    public String saveBooking(Long roomId, BookedRoom bookingRequest) {
        if (bookingRequest.getCheckOutDate().isBefore(bookingRequest.getCheckInDate())) {
            throw new InvalidBookingRequestException("Check-in date must come before check-out date");
        }

        Room room = roomService.getRoomById(roomId).orElseThrow(() ->
                new ResourceNotFoundException("Room not found with id: " + roomId));

        // Tính toán tổng số tiền thanh toán
        Float totalPrice = calculatePaymentAmount(bookingRequest, room);

        bookingRequest.setTotalPrice(totalPrice);


        List<BookedRoom> existingBookings = room.getBookings();
        boolean roomIsAvailable = roomIsAvailable(bookingRequest, existingBookings);

        if (roomIsAvailable) {
            bookingRequest.setDescription(bookingRequest.getDescription());
            room.addBooking(bookingRequest);
            bookingRepository.save(bookingRequest);
        } else {
            throw new InvalidBookingRequestException("Sorry, This room is not available for the selected dates;");
        }

        return bookingRequest.getBookingConfirmationCode();
    }
    public Float calculatePaymentAmount(BookedRoom bookingRequest, Room room) {
        // Tính số ngày giữa check-in và check-out
        long diffInDays = bookingRequest.getCheckOutDate().toEpochDay() - bookingRequest.getCheckInDate().toEpochDay();

        // Tính toán tổng số tiền và chuyển đổi về kiểu Float
        BigDecimal days = BigDecimal.valueOf(diffInDays);
        BigDecimal totalPrice = room.getRoomPrice().multiply(days);

        return totalPrice.floatValue(); // Chuyển đổi về Float
    }

    public BookedRoom findByBookingConfirmationCode(String confirmationCode) {
        return bookingRepository.findByBookingConfirmationCode(confirmationCode)
                .orElseThrow(()->new ResourceNotFoundException("No booking found with booking code:"+confirmationCode));
    }


    private boolean roomIsAvailable(BookedRoom bookingRequest, List<BookedRoom> existingBookings) {
        return existingBookings.stream()
                .noneMatch(existingBooking ->
                        bookingRequest.getCheckInDate().equals(existingBooking.getCheckInDate())
                                || bookingRequest.getCheckOutDate().isBefore(existingBooking.getCheckOutDate())
                                || (bookingRequest.getCheckInDate().isAfter(existingBooking.getCheckInDate())
                                && bookingRequest.getCheckInDate().isBefore(existingBooking.getCheckOutDate()))
                                || (bookingRequest.getCheckInDate().isBefore(existingBooking.getCheckInDate())

                                && bookingRequest.getCheckOutDate().equals(existingBooking.getCheckOutDate()))
                                || (bookingRequest.getCheckInDate().isBefore(existingBooking.getCheckInDate())

                                && bookingRequest.getCheckOutDate().isAfter(existingBooking.getCheckOutDate()))

                                || (bookingRequest.getCheckInDate().equals(existingBooking.getCheckOutDate())
                                && bookingRequest.getCheckOutDate().equals(existingBooking.getCheckInDate()))

                                || (bookingRequest.getCheckInDate().equals(existingBooking.getCheckOutDate())
                                && bookingRequest.getCheckOutDate().equals(bookingRequest.getCheckInDate()))
                );
    }
}

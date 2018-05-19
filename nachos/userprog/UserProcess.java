package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {

		boolean status = Machine.interrupt().disable();
		fileTable = new OpenFile[16];
		fileDescriptorQueue = new LinkedList<Integer>();
		for(int i = 2; i < 16; i++){
			fileDescriptorQueue.add(i);
		}

		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

		childProcesses = new LinkedList<UserProcess>();
		PID = nextPID;
		nextPID++;

		Machine.interrupt().restore(status);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		associatedUThread = new UThread(this);
		associatedUThread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	@SuppressWarnings("Duplicates")
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// check that the vaddr is valid. It is invalid if the vpn is greater than the max vpn given by the length
		// of the pageTable
		int vpn = Processor.pageFromAddress(vaddr);
		if (vaddr < 0 || vpn >= pageTable.length)
			return 0;

		int physical_offset = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = ppn*pageSize + physical_offset;

		int amount = Math.min(length, (pageTable.length * pageSize) - vaddr);
		System.arraycopy(memory, paddr, data, offset, amount);

		return amount;
//------------------------------------------------------------------------------------------------------------------
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);
//
//		byte[] memory = Machine.processor().getMemory();
//
//		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(memory, vaddr, data, offset, amount);
//
//		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	@SuppressWarnings("Duplicates")
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// check that the vaddr is valid. It is invalid if the vpn is greater than the max vpn given by the length
		// of the pageTable
		int vpn = Processor.pageFromAddress(vaddr);
		if (vaddr < 0 || vpn >= pageTable.length)
			return 0;

		int physical_offset = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = ppn*pageSize + physical_offset;

		int amount = Math.min(length, (pageTable.length * pageSize) - vaddr);
		System.arraycopy(data, offset, memory, paddr, amount);
		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		boolean status = Machine.interrupt().disable();

		// if the number of pages requires is greater than freePhysicalPages.size() (the number of physical pages
		// available), then fail
		if(numPages >= UserKernel.freePhysicalPages.size()){
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, UserKernel.freePhysicalPages.poll(), true, false, false, false);
		Machine.interrupt().restore(status);

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);


			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// Set the TranslationEntry to be readOnly according to the section's readOnly property
				pageTable[vpn].readOnly = section.isReadOnly();
				// the physical page is given by the TranslationEntry at the index given by the vpn
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		boolean status = Machine.interrupt().disable();
		coff.close();
		for(int i = 0; i < pageTable.length; i++){
			UserKernel.freePhysicalPages.add(pageTable[i].ppn);
		}
		Machine.interrupt().restore(status);
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		//TODO: Make it so that halt works if the last process is calling it
		if(PID != 0){
			return 0;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		//TODO: make it so that the final process calls KThread.kernel.terminate
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		unloadSections();
		for(int i = 0; i < fileTable.length; i++ ){
			if(fileTable[i] != null){
				fileTable[i].close();
				fileTable[i] = null;
			}
		}
		exitStatus = status;
		UThread.finish();
		return 0;
	}

	/**
	 * Handle the creat() system call
	 * @param vaddrFileName the virtual address of the filename for the new file
	 * @return
	 */
	private int handleCreat(int vaddrFileName){

		if(vaddrFileName <= 0 || Processor.pageFromAddress(vaddrFileName) >= pageTable.length){
			return ERROR;
		}

		String filename = readVirtualMemoryString(vaddrFileName, 256);
		if(filename == null){
			return ERROR;
		}

		OpenFile newFile = ThreadedKernel.fileSystem.open(filename,true);

		// If the file descriptor queue is empty, the max number of open files has been reached
		if(newFile != null && fileDescriptorQueue.peek() != null){
			int descriptor = fileDescriptorQueue.poll();
			fileTable[descriptor] = newFile;
			return descriptor;
		}

		return ERROR;
	}

	private int handleWrite(int descriptor, int vaddrReadBuffer, int maxBytesWritten){


		if( vaddrReadBuffer <= 0 || Processor.pageFromAddress(vaddrReadBuffer) >= pageTable.length){
			return ERROR;
		}

		if(descriptor < 0 || descriptor > 15 || fileTable[descriptor] == null){
			return ERROR;
		}

		if(maxBytesWritten < 0) {
			return ERROR;
		}


		writeLock.acquire();
		byte[] writeBuffer = new byte[pageSize];
		if(maxBytesWritten <= pageSize){

			readVirtualMemory(vaddrReadBuffer, writeBuffer,0,maxBytesWritten);
			int bytesWritten = fileTable[descriptor].write(writeBuffer,0,maxBytesWritten);
			if(bytesWritten != maxBytesWritten){
				writeLock.release();
				return ERROR;
			}
			writeLock.release();
			return bytesWritten;

		} else {
			int bytesWrittenActual = 0;
			int bytesWrittenExpected = 0;
			int numBytesRetrieved = pageSize;
			while( bytesWrittenExpected < maxBytesWritten) {
				readVirtualMemory(vaddrReadBuffer,writeBuffer,0,numBytesRetrieved);
				bytesWrittenExpected += numBytesRetrieved;
				bytesWrittenActual += fileTable[descriptor].write(writeBuffer,0,numBytesRetrieved);
				vaddrReadBuffer += numBytesRetrieved;
				if(maxBytesWritten - bytesWrittenExpected < pageSize){
					numBytesRetrieved = maxBytesWritten - bytesWrittenExpected;
				}
			}
			if(bytesWrittenActual != maxBytesWritten){
				writeLock.release();
				return ERROR;
			}
			writeLock.release();
			return bytesWrittenActual;
		}


	}

	/**
	 * Handle the open() syscall
	 * @param vaddrFileName virtual address of the filename to attempt to open
	 * @return
	 */
	private int handleOpen(int vaddrFileName){

		if(vaddrFileName <= 0 || Processor.pageFromAddress(vaddrFileName) >= pageTable.length){
			return ERROR;
		}

		String filename = readVirtualMemoryString(vaddrFileName, 256);
		if(filename == null){
			return ERROR;
		}

		OpenFile newFile = ThreadedKernel.fileSystem.open(filename,false);

		// If the file descriptor queue is empty, the max number of open files has been reached
		if(newFile != null && fileDescriptorQueue.peek() != null){
			int descriptor = fileDescriptorQueue.poll();
			fileTable[descriptor] = newFile;
			return descriptor;
		}

		return ERROR;
	}

	private int handleClose(int descriptor){
		if(descriptor < 0 || descriptor > 15 || fileTable[descriptor] == null){
			return ERROR;
		}

		fileTable[descriptor].close();
		fileTable[descriptor] = null;
		fileDescriptorQueue.add(descriptor);
		return 0;
	}

	private int handleRead(int descriptor, int vaddrReadBuffer, int maxBytesRead){

		if( vaddrReadBuffer <= 0 || Processor.pageFromAddress(vaddrReadBuffer) >= pageTable.length){
			return ERROR;
		}

		if(descriptor < 0 || descriptor > 15 || fileTable[descriptor] == null){
			return ERROR;
		}

		if(maxBytesRead < 0) {
			return ERROR;
		}

		readLock.acquire();

		byte[] readBuffer = new byte[pageSize];
		if(maxBytesRead <= pageSize){

			int bytesRead = fileTable[descriptor].read(readBuffer,0,maxBytesRead);
			writeVirtualMemory(vaddrReadBuffer,readBuffer,0,maxBytesRead);
			readLock.release();
			return bytesRead;

		} else {
			int bytesReadActual = 0;
			int bytesReadExpected = 0;
			int numBytesRetrieved = pageSize;
			while( bytesReadExpected < maxBytesRead) {

				bytesReadActual += fileTable[descriptor].read(readBuffer,0,numBytesRetrieved);
				bytesReadExpected += numBytesRetrieved;
				writeVirtualMemory(vaddrReadBuffer,readBuffer);
				vaddrReadBuffer += numBytesRetrieved;
				if(maxBytesRead - bytesReadExpected < pageSize){
					numBytesRetrieved = maxBytesRead - bytesReadExpected;
				}
			}
			readLock.release();
			return bytesReadActual;
		}
	}

	private int handleUnlink(int vaddrFileName) {
		if (vaddrFileName <= 0 || Processor.pageFromAddress(vaddrFileName) >= pageTable.length) {
			return ERROR;
		}
		String fileName = readVirtualMemoryString(vaddrFileName, 256);
		if (fileName == null) {
			return ERROR;
		}
		boolean successful = ThreadedKernel.fileSystem.remove(fileName);
		if (successful) {
			return 0;
		}
		return -1;
	}

	/**
	 * Handles the exec() system call
	 * @param vaddrFileName Pointer to the vaddress where the filename is stored
	 * @param argc Number of arguments to pass to the new process
	 * @param vaddrArgv Pointer to the vaddress of an array of other pointers for the arguments
	 * @return the PID of the new process if successful or -1 if not
	 */
	private int handleExec(int vaddrFileName, int argc, int vaddrArgv){
		// First, check validity of each argument

		if(vaddrFileName <= 0 || Processor.pageFromAddress(vaddrFileName) >= pageTable.length) {
			return ERROR;
		}

		if(argc < 0){
			return ERROR;
		}

		if(vaddrArgv <= 0 || Processor.pageFromAddress(vaddrArgv) >= pageTable.length){
			return ERROR;
		}

		// Get filename from vaddr
		String fileName = readVirtualMemoryString(vaddrFileName,256);
		if(fileName == null){
			return ERROR;
		}


		// Need to get strings from the array of char pointers passed in, but we just have a single pointer. Accessing
		// them iteratively after retrieving the full array of pointers.
		byte[] charPointerArray = new byte[INTLENGTH*argc];
		int bytesRead = readVirtualMemory(vaddrArgv,charPointerArray);

		// It should probably be an error if we don't retrieve the entire array of pointers
		if(bytesRead < charPointerArray.length){
			return ERROR;
		}

		String[] argv = new String[argc];
		// Iteratively getting the pointer from the array of pointers, then retrieving the string from that pointer\
		// Reading the array backwards because of endianness
		for(int i = argc; i > 0; i--){
			int vaddrArgString = 0;
			vaddrArgString += charPointerArray[i*INTLENGTH-1];
			for(int j = 2; j <= 4; j++){
				vaddrArgString = vaddrArgString << 8;
				vaddrArgString += charPointerArray[i*INTLENGTH-j];
			}

			if(vaddrArgString <= 0 || Processor.pageFromAddress(vaddrArgString) >= pageTable.length){
				return ERROR;
			}
			argv[i-1] = readVirtualMemoryString(vaddrArgString,256);
		}

		// Make a new process, pass it the filename and arguments, then add it to this process's list of
		// child processes
        UserProcess process = UserProcess.newUserProcess();
        Lib.assertTrue(process.execute(fileName,argv));
		childProcesses.add(process);

		return process.getPID();
	}

	/**
	 * handles the join() syscall. Can only be called by a parent on its child
	 * @param PID the PID of the child process to join on
	 * @param vaddrStatus the virtual address where the exit status of the child is stored
	 * @return the exit status of the child process
	 */
	private int handleJoin(int PID, int vaddrStatus){

		// Verify that vaddrStatus is a valid virtual address
		if(vaddrStatus <= 0 || Processor.pageFromAddress(vaddrStatus) >= pageTable.length){
			return ERROR;
		}

		// Verify that given PID is a valid child PID
		if(childProcesses.isEmpty()){
			return ERROR;
		}

		UserProcess childProcess;
		for(int i = 0; i < childProcesses.size(); i++){
			childProcess = childProcesses.get(i);
			if(childProcess.getPID() == PID){
				childProcess.getAssociatedUThread().join();
				childProcesses.remove(i);
				int childExitStatus = childProcess.getExitStatus();
				if(childExitStatus == -1){
					return 0;
				} else {
					byte[] exitStatusBytes = new byte[INTLENGTH];
					exitStatusBytes[0] = (byte) (0x000000FF & childExitStatus);
					exitStatusBytes[1] = (byte) (0x0000FF00 & childExitStatus);
					exitStatusBytes[2] = (byte) (0x00FF0000 & childExitStatus);
					exitStatusBytes[3] = (byte) (0xFF000000 & childExitStatus);
					int bytesWritten = writeVirtualMemory(vaddrStatus,exitStatusBytes);
					if(bytesWritten < INTLENGTH){
						return ERROR;
					}
					return 1;
				}
			}
		}


		return ERROR;
	}
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

		switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                return handleExit(a0);
            case syscallCreate:
                return handleCreat(a0);
			case syscallWrite:
				return handleWrite(a0,a1,a2);
			case syscallOpen:
				return handleOpen(a0);
			case syscallClose:
				return handleClose(a0);
			case syscallRead:
				return handleRead(a0,a1,a2);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExec:
				return handleExec(a0,a1,a2);
			case syscallJoin:
				return handleJoin(a0,a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		boolean status = Machine.interrupt().disable();
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(-1);
		}
		Machine.interrupt().restore(status);
	}


	public int getPID(){
		return PID;
	}

	public UThread getAssociatedUThread()
	{
		return associatedUThread;
	}

	/**
	 * Getter for the exit status of this process. It is an error to call this process
	 * @return
	 */
	public int getExitStatus(){
		return exitStatus;
	}
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] fileTable;

	private LinkedList<Integer> fileDescriptorQueue;
	private static final int INTLENGTH = 4;

	private static final int ERROR = -1;

	private int PID;

	private LinkedList<UserProcess> childProcesses;

	private static int nextPID = 1;

	private UThread associatedUThread;

	private static Lock readLock = new Lock();
	private static Lock writeLock = new Lock();

	private int exitStatus;

}
